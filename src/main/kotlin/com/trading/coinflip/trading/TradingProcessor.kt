package com.trading.coinflip.trading

import com.trading.coinflip.config.TradingConfig
import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.Position
import com.trading.coinflip.model.PositionSide
import com.trading.coinflip.model.PositionStatus
import com.trading.coinflip.model.Trade
import com.trading.coinflip.strategy.CoinFlipStrategy
import mu.KotlinLogging
import java.math.BigDecimal

/**
 * Core trading logic processor.
 * Encapsulates position processing, P&L calculation, and drawdown tracking.
 * Thread-safe when used with separate TradingState instances.
 */
object TradingProcessor {
    private val log = KotlinLogging.logger {}

    /**
     * Process a single candle: update positions, close stops, consider new entries.
     */
    fun processCandle(
        state: TradingState,
        candle: Candle,
        candles: List<Candle>,
        candleIndex: Int,
        config: TradingConfig,
    ) {
        // Update existing positions and check for stops
        val positionsToClose = mutableListOf<Position>()
        for (position in state.openPositions) {
            val shouldClose =
                CoinFlipStrategy.updatePosition(
                    position = position,
                    candle = candle,
                    candles = candles,
                    candleIndex = candleIndex,
                    atrMultiplier = config.atrMultiplier,
                )

            if (shouldClose) {
                positionsToClose.add(position)
            }
        }

        // Close positions and update balance
        for (position in positionsToClose) {
            closePosition(state, position, config.transactionCostPercent)
        }

        // Consider opening new position if we have capacity
        if (state.openPositions.size < config.maxConcurrentPositions &&
            candle.atr != null &&
            state.accountBalance > BigDecimal.ZERO
        ) {
            // Calculate capital already allocated to open positions
            val allocatedCapital = state.openPositions.sumOf { it.allocatedCapital }
            val availableBalance = state.accountBalance - allocatedCapital

            // Only try to open if we have available capital
            if (availableBalance > BigDecimal.ZERO) {
                // Random entry frequency based on config
                if (kotlin.random.Random.nextDouble() < config.entryFrequency) {
                    val newPosition =
                        CoinFlipStrategy.createPosition(
                            candle = candle,
                            candles = candles,
                            candleIndex = candleIndex,
                            accountBalance = availableBalance,
                            riskPercent = config.riskPerTrade,
                            atrMultiplier = config.atrMultiplier,
                            balanceBeforeOpen = availableBalance,
                            positionId = ++state.positionIdCounter,
                        )

                    if (newPosition != null) {
                        state.openPositions.add(newPosition)
                        log.debug { "Opened new ${newPosition.side} position at ${newPosition.entryPrice}" }
                    }
                }
            }
        }
    }

    /**
     * Close a position and update balances, track drawdown.
     */
    fun closePosition(
        state: TradingState,
        position: Position,
        transactionCostPercent: BigDecimal,
    ): Trade {
        state.openPositions.remove(position)
        position.status = PositionStatus.CLOSED

        val balanceBeforeClose = state.accountBalance

        // Calculate P&L and transaction costs
        val pnl =
            when (position.side) {
                PositionSide.LONG -> (position.exitPrice!! - position.entryPrice) * position.positionSize
                PositionSide.SHORT -> (position.entryPrice - position.exitPrice!!) * position.positionSize
            }
        val transactionCost =
            position.entryPrice * position.positionSize *
                (transactionCostPercent / BigDecimal(100)) * BigDecimal(2) // Entry + Exit

        state.accountBalance += pnl - transactionCost
        val balanceAfterClose = state.accountBalance

        val trade = position.toTrade(++state.tradeIdCounter, balanceBeforeClose, balanceAfterClose)
        state.closedTrades.add(trade)

        log.debug {
            "Closed ${trade.side} trade: P/L ${trade.profitLoss} (${trade.profitLossPercent}%), " +
                "Balance: ${state.accountBalance}"
        }

        // Track drawdown
        if (state.accountBalance > state.peakBalance) {
            state.peakBalance = state.accountBalance
        }
        val currentDrawdown = state.peakBalance - state.accountBalance
        if (currentDrawdown > state.maxDrawdown) {
            state.maxDrawdown = currentDrawdown
        }

        return trade
    }

    /**
     * Force close a position at a specific price (e.g., end of backtest period).
     */
    fun forceClosePosition(
        state: TradingState,
        position: Position,
        exitPrice: BigDecimal,
        exitTime: java.time.Instant,
        exitReason: String,
        transactionCostPercent: BigDecimal,
    ): Trade {
        position.exitPrice = exitPrice
        position.exitTime = exitTime
        position.exitReason = exitReason
        position.status = PositionStatus.CLOSED

        return closePosition(state, position, transactionCostPercent)
    }
}
