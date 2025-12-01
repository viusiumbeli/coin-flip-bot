package com.trading.coinflip.engine

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.model.Position
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.PositionUpdateResult
import com.trading.coinflip.engine.model.TradingEvent
import com.trading.coinflip.engine.model.TradingState
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import kotlin.random.Random

/**
 * Core trading logic processor.
 * Pure functions that return events instead of mutating state.
 * Thread-safe as it has no mutable state.
 */
@Component
class TradingProcessor(
    private val strategy: CoinFlipStrategy,
    properties: BacktestProperties,
) {
    private val config = properties.trading
    private val log = KotlinLogging.logger {}

    /**
     * Process a single candle: update positions, close stops, consider new entries.
     * Returns a list of events describing what should change.
     */
    fun processCandle(
        state: TradingState,
        candle: CandleEntity,
    ): List<TradingEvent> {
        val events = mutableListOf<TradingEvent>()
        var currentBalance = state.accountBalance
        var currentPeakBalance = state.peakBalance
        var currentMaxDrawdown = state.maxDrawdown
        var positionIdCounter = state.positionIdCounter
        var tradeIdCounter = state.tradeIdCounter

        // Track positions that will remain open after this candle
        val updatedPositions = mutableListOf<Position>()

        // Update existing positions and check for stops
        for (position in state.openPositions) {
            val result =
                strategy.updatePosition(
                    position = position,
                    candle = candle,
                    atrMultiplier = config.atrMultiplier,
                )

            when (result) {
                is PositionUpdateResult.StopHit -> {
                    // Create close event
                    val closeEvent =
                        createCloseEvent(
                            position = position,
                            exitPrice = result.exitPrice,
                            exitTime = result.exitTime,
                            exitReason = result.exitReason,
                            currentBalance = currentBalance,
                            currentPeakBalance = currentPeakBalance,
                            currentMaxDrawdown = currentMaxDrawdown,
                            tradeIdCounter = tradeIdCounter,
                        )
                    events.add(closeEvent)

                    // Update running state for subsequent calculations
                    currentBalance = closeEvent.newBalance
                    currentPeakBalance = closeEvent.newPeakBalance
                    currentMaxDrawdown = closeEvent.newMaxDrawdown
                    tradeIdCounter = closeEvent.newTradeIdCounter

                    log.debug {
                        "Closed ${position.side} trade: P/L ${closeEvent.pnl}, Balance: $currentBalance"
                    }
                }

                is PositionUpdateResult.Updated -> {
                    events.add(
                        TradingEvent.PositionUpdated(
                            positionId = position.id,
                            newTrailingStop = result.newTrailingStop,
                            newHighestFavorablePrice = result.newHighestFavorablePrice,
                        ),
                    )
                    updatedPositions.add(position)
                }

                is PositionUpdateResult.NoChange -> {
                    updatedPositions.add(position)
                }
            }
        }

        // Consider opening new position if we have capacity
        val openPositionCount = updatedPositions.size
        if (openPositionCount < config.maxConcurrentPositions &&
            candle.atr != null &&
            currentBalance > BigDecimal.ZERO
        ) {
            // Calculate capital already allocated to open positions
            val allocatedCapital = updatedPositions.sumOf { it.allocatedCapital }
            val availableBalance = currentBalance - allocatedCapital

            // Only try to open if we have available capital
            if (availableBalance > BigDecimal.ZERO) {
                // Random entry frequency based on config
                if (Random.nextDouble() < config.entryFrequency) {
                    val newPositionId = positionIdCounter + 1
                    val newPosition =
                        strategy.createPosition(
                            candle = candle,
                            accountBalance = availableBalance,
                            riskPercent = config.riskPerTrade,
                            atrMultiplier = config.atrMultiplier,
                            balanceBeforeOpen = availableBalance,
                            positionId = newPositionId,
                        )

                    newPosition?.let {
                        positionIdCounter = newPositionId
                        events.add(
                            TradingEvent.PositionOpened(
                                position = it,
                                newPositionIdCounter = positionIdCounter,
                            ),
                        )
                        log.debug { "Opened new ${it.side} position at ${it.entryPrice}" }
                    }
                }
            }
        }

        return events
    }

    /**
     * Force close a position at a specific price (e.g., end of backtest period).
     * Returns a PositionClosed event.
     */
    fun forceClosePosition(
        state: TradingState,
        position: Position,
        exitPrice: BigDecimal,
        exitTime: Instant,
        exitReason: String,
    ): TradingEvent.PositionClosed =
        createCloseEvent(
            position = position,
            exitPrice = exitPrice,
            exitTime = exitTime,
            exitReason = exitReason,
            currentBalance = state.accountBalance,
            currentPeakBalance = state.peakBalance,
            currentMaxDrawdown = state.maxDrawdown,
            tradeIdCounter = state.tradeIdCounter,
        )

    /**
     * Create a PositionClosed event with P&L and drawdown calculations.
     */
    private fun createCloseEvent(
        position: Position,
        exitPrice: BigDecimal,
        exitTime: Instant,
        exitReason: String,
        currentBalance: BigDecimal,
        currentPeakBalance: BigDecimal,
        currentMaxDrawdown: BigDecimal,
        tradeIdCounter: Long,
    ): TradingEvent.PositionClosed {
        // Calculate P&L
        val pnl =
            when (position.side) {
                PositionSide.LONG -> (exitPrice - position.entryPrice) * position.positionSize
                PositionSide.SHORT -> (position.entryPrice - exitPrice) * position.positionSize
            }

        // Calculate transaction cost (entry + exit)
        val transactionCost =
            position.entryPrice * position.positionSize *
                (config.transactionCostPercent / BigDecimal(100)) * BigDecimal(2)

        // New balance after P&L and costs
        val newBalance = currentBalance + pnl - transactionCost

        // Track drawdown
        val newPeakBalance = maxOf(currentPeakBalance, newBalance)
        val newMaxDrawdown = maxOf(currentMaxDrawdown, newPeakBalance - newBalance)

        // Create closed position for trade conversion
        val closedPosition =
            position.copy(
                exitPrice = exitPrice,
                exitTime = exitTime,
                exitReason = exitReason,
                status = PositionStatus.CLOSED,
            )

        val newTradeIdCounter = tradeIdCounter + 1
        val trade = closedPosition.toTrade(newTradeIdCounter, currentBalance, newBalance)

        return TradingEvent.PositionClosed(
            positionId = position.id,
            exitPrice = exitPrice,
            exitTime = exitTime,
            exitReason = exitReason,
            pnl = pnl,
            transactionCost = transactionCost,
            trade = trade,
            newBalance = newBalance,
            newPeakBalance = newPeakBalance,
            newMaxDrawdown = newMaxDrawdown,
            newTradeIdCounter = newTradeIdCounter,
        )
    }
}
