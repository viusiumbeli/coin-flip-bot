package com.trading.coinflip.engine.model

import com.trading.coinflip.common.config.TradingConfig
import com.trading.coinflip.engine.model.Trade
import com.trading.coinflip.data.CandleEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.random.Random

/**
 * Mutable state container for high-performance backtest execution.
 * Avoids object allocation by mutating in place.
 * Implements TradingStateView for compatibility with TradingProcessor.
 */
class MutableTradingState(
    override var accountBalance: BigDecimal,
    override var peakBalance: BigDecimal,
    override var maxDrawdown: BigDecimal,
    private val _openPositions: MutableList<MutablePosition>,
    private val _closedTrades: MutableList<Trade>,
    override var tradeIdCounter: Long,
    override var positionIdCounter: Long,
    /** Running total of allocated capital - avoids sumOf on every candle */
    private var totalAllocatedCapital: BigDecimal,
) : TradingStateView {
    // TradingStateView requires List<PositionView>
    // MutableList<MutablePosition> is covariant to List<PositionView>
    override val openPositions: List<PositionView>
        get() = _openPositions

    override val closedTrades: List<Trade>
        get() = _closedTrades

    companion object {
        // Pre-computed constants to avoid per-call BigDecimal allocations
        private val MIN_RISK_AMOUNT = BigDecimal("0.01")

        fun create(initialCapital: BigDecimal): MutableTradingState =
            MutableTradingState(
                accountBalance = initialCapital,
                peakBalance = initialCapital,
                maxDrawdown = BigDecimal.ZERO,
                _openPositions = mutableListOf(),
                _closedTrades = mutableListOf(),
                tradeIdCounter = 0L,
                positionIdCounter = 0L,
                totalAllocatedCapital = BigDecimal.ZERO,
            )
    }

    /**
     * Apply a single event by mutating this state.
     * No object allocation occurs.
     */
    fun applyEvent(event: TradingEvent) {
        when (event) {
            is TradingEvent.PositionOpened -> {
                _openPositions.add(event.position.toMutable())
                positionIdCounter = event.newPositionIdCounter
            }

            is TradingEvent.PositionUpdated -> {
                _openPositions.find { it.id == event.positionId }?.apply {
                    trailingStop = event.newTrailingStop
                    highestFavorablePrice = event.newHighestFavorablePrice
                }
            }

            is TradingEvent.PositionClosed -> {
                _openPositions.removeIf { it.id == event.positionId }
                _closedTrades.add(event.trade)
                accountBalance = event.newBalance
                peakBalance = event.newPeakBalance
                maxDrawdown = event.newMaxDrawdown
                tradeIdCounter = event.newTradeIdCounter
            }
        }
    }

    /**
     * Apply multiple events by mutating this state.
     */
    fun applyEvents(events: List<TradingEvent>) {
        for (event in events) {
            applyEvent(event)
        }
    }

    /**
     * Get mutable positions list for force-closing at end of backtest.
     */
    fun getMutableOpenPositions(): MutableList<MutablePosition> = _openPositions

    // ============================================================================
    // Direct Mutation Methods - Bypass event system for maximum performance
    // ============================================================================

    /**
     * Process candle with direct mutation - no object allocations.
     * Used by BacktestEngine for maximum performance.
     */
    fun processCandleDirect(
        candle: CandleEntity,
        config: TradingConfig,
        random: Random,
    ) {
        val atr = candle.atr ?: return
        if (atr <= BigDecimal.ZERO) return

        // Index-based backwards loop - avoids Iterator allocation (510M allocations eliminated)
        var i = _openPositions.size - 1
        while (i >= 0) {
            val position = _openPositions[i]

            // Update trailing stop directly (no TrailingStopUpdate allocation)
            val isFavorableMove =
                when (position.side) {
                    PositionSide.LONG -> candle.close > position.highestFavorablePrice
                    PositionSide.SHORT -> candle.close < position.highestFavorablePrice
                }

            if (isFavorableMove) {
                position.highestFavorablePrice = candle.close
                val newStop =
                    when (position.side) {
                        PositionSide.LONG -> candle.close - (atr * config.atrMultiplier)
                        PositionSide.SHORT -> candle.close + (atr * config.atrMultiplier)
                    }
                val shouldUpdate =
                    when (position.side) {
                        PositionSide.LONG -> newStop > position.trailingStop
                        PositionSide.SHORT -> newStop < position.trailingStop
                    }
                if (shouldUpdate) {
                    position.trailingStop = newStop
                }
            }

            // Check if stop hit
            val isStopHit =
                when (position.side) {
                    PositionSide.LONG -> candle.close <= position.trailingStop
                    PositionSide.SHORT -> candle.close >= position.trailingStop
                }

            if (isStopHit) {
                // Close position directly (creates Trade - unavoidable)
                closePositionDirect(position, position.trailingStop, candle.openTime, "Trailing stop hit", config)
                _openPositions.removeAt(i)
            }
            i--
        }

        // Consider opening new position
        if (_openPositions.size < config.maxConcurrentPositions && accountBalance > BigDecimal.ZERO) {
            val availableBalance = accountBalance - totalAllocatedCapital

            if (availableBalance > BigDecimal.ZERO && random.nextDouble() < config.entryFrequency) {
                createPositionDirect(candle, availableBalance, config, random)
            }
        }
    }

    /**
     * Close a position directly without creating TradingEvent.
     */
    fun closePositionDirect(
        position: MutablePosition,
        exitPrice: BigDecimal,
        exitTime: Instant,
        exitReason: String,
        config: TradingConfig,
    ) {
        // Calculate P&L
        val pnl =
            when (position.side) {
                PositionSide.LONG -> (exitPrice - position.entryPrice) * position.positionSize
                PositionSide.SHORT -> (position.entryPrice - exitPrice) * position.positionSize
            }

        // Calculate transaction cost using pre-computed rate
        val transactionCost = position.entryPrice * position.positionSize * config.roundTripTransactionCostRate

        // Update state directly
        val balanceBeforeClose = accountBalance
        accountBalance = accountBalance + pnl - transactionCost
        peakBalance = maxOf(peakBalance, accountBalance)
        maxDrawdown = maxOf(maxDrawdown, peakBalance - accountBalance)
        tradeIdCounter++

        // Release allocated capital
        totalAllocatedCapital -= position.allocatedCapital

        // Update position for trade conversion
        position.exitPrice = exitPrice
        position.exitTime = exitTime
        position.exitReason = exitReason
        position.status = PositionStatus.CLOSED

        // Create Trade (only allocation - unavoidable for results)
        _closedTrades.add(position.toTrade(tradeIdCounter, balanceBeforeClose, accountBalance))
    }

    /**
     * Create and open a new position directly without creating TradingEvent.
     */
    private fun createPositionDirect(
        candle: CandleEntity,
        availableBalance: BigDecimal,
        config: TradingConfig,
        random: Random,
    ) {
        val atr = candle.atr ?: return
        if (atr <= BigDecimal.ZERO) return

        val side = if (random.nextBoolean()) PositionSide.LONG else PositionSide.SHORT
        val entryPrice = candle.close

        // Calculate initial stop loss based on ATR
        val initialStopLoss =
            when (side) {
                PositionSide.LONG -> entryPrice - (atr * config.atrMultiplier)
                PositionSide.SHORT -> entryPrice + (atr * config.atrMultiplier)
            }

        // Check minimum balance requirement using pre-computed rate
        val riskAmount = availableBalance * config.riskPerTradeRate
        if (riskAmount < MIN_RISK_AMOUNT) return

        // Calculate position size
        val stopDistance = (entryPrice - initialStopLoss).abs()
        if (stopDistance <= BigDecimal.ZERO) return

        var positionSize = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP)
        if (positionSize <= BigDecimal.ZERO) return

        // Cap position size to available balance
        val positionValue = positionSize * entryPrice
        if (positionValue > availableBalance) {
            positionSize = availableBalance.divide(entryPrice, 8, RoundingMode.DOWN)
            if (positionSize <= BigDecimal.ZERO) return
        }

        // Calculate allocated capital
        val allocatedCapital = positionSize * entryPrice
        val balanceAfterOpen = availableBalance - allocatedCapital

        // Track allocated capital
        totalAllocatedCapital += allocatedCapital

        // Create and add position directly
        positionIdCounter++
        _openPositions.add(
            MutablePosition(
                id = positionIdCounter,
                symbol = candle.symbol,
                timeframe = candle.timeframe,
                side = side,
                entryTime = candle.openTime,
                entryPrice = entryPrice,
                positionSize = positionSize,
                initialStopLoss = initialStopLoss,
                trailingStop = initialStopLoss,
                highestFavorablePrice = entryPrice,
                status = PositionStatus.OPEN,
                balanceBeforeOpen = availableBalance,
                balanceAfterOpen = balanceAfterOpen,
                allocatedCapital = allocatedCapital,
            ),
        )
    }
}
