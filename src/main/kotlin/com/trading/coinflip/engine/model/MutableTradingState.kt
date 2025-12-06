package com.trading.coinflip.engine.model

import java.math.BigDecimal

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
    private val collectTrades: Boolean,
    override val stats: RunningTradeStats,
) : TradingStateView {
    // TradingStateView requires List<PositionView>
    // MutableList<MutablePosition> is covariant to List<PositionView>
    override val openPositions: List<PositionView>
        get() = _openPositions

    override val closedTrades: List<Trade>
        get() = _closedTrades

    companion object {
        fun create(
            initialCapital: BigDecimal,
            collectTrades: Boolean = false,
        ): MutableTradingState =
            MutableTradingState(
                accountBalance = initialCapital,
                peakBalance = initialCapital,
                maxDrawdown = BigDecimal.ZERO,
                _openPositions = mutableListOf(),
                _closedTrades = mutableListOf(),
                tradeIdCounter = 0L,
                positionIdCounter = 0L,
                collectTrades = collectTrades,
                stats = RunningTradeStats(),
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
                stats.addTrade(event.trade)
                if (collectTrades) {
                    _closedTrades.add(event.trade)
                }
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
}
