package com.trading.coinflip.engine

import com.trading.coinflip.common.config.TradingConfig
import com.trading.coinflip.common.model.Trade
import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.model.Position
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import kotlin.random.Random

/**
 * Core trading logic processor.
 * Encapsulates position processing, P&L calculation, and drawdown tracking.
 * Thread-safe when used with separate TradingState instances.
 */
@Component
class TradingProcessor(
    private val strategy: CoinFlipStrategy,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Process a single candle: update positions, close stops, consider new entries.
     */
    fun processCandle(
        state: TradingState,
        candle: CandleEntity,
        candles: List<CandleEntity>,
        candleIndex: Int,
        config: TradingConfig,
    ) {
        // Update existing positions and check for stops
        val positionsToClose =
            state.openPositions.filter { position ->
                strategy.updatePosition(
                    position = position,
                    candle = candle,
                    candles = candles,
                    candleIndex = candleIndex,
                    atrMultiplier = config.atrMultiplier,
                )
            }

        // Close positions and update balance
        positionsToClose.forEach { closePosition(state, it, config.transactionCostPercent) }

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
                if (Random.nextDouble() < config.entryFrequency) {
                    val newPosition =
                        strategy.createPosition(
                            candle = candle,
                            candles = candles,
                            candleIndex = candleIndex,
                            accountBalance = availableBalance,
                            riskPercent = config.riskPerTrade,
                            atrMultiplier = config.atrMultiplier,
                            balanceBeforeOpen = availableBalance,
                            positionId = ++state.positionIdCounter,
                        )

                    newPosition?.let {
                        state.openPositions.add(it)
                        log.debug { "Opened new ${it.side} position at ${it.entryPrice}" }
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
        state.peakBalance = maxOf(state.peakBalance, state.accountBalance)
        state.maxDrawdown = maxOf(state.maxDrawdown, state.peakBalance - state.accountBalance)

        return trade
    }

    /**
     * Force close a position at a specific price (e.g., end of backtest period).
     */
    fun forceClosePosition(
        state: TradingState,
        position: Position,
        exitPrice: BigDecimal,
        exitTime: Instant,
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
