package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Trade
import java.math.BigDecimal

/**
 * Immutable state container for trading operations.
 * Use copy() or applyEvent() to create modified versions.
 */
data class TradingState(
    val accountBalance: BigDecimal,
    val peakBalance: BigDecimal,
    val maxDrawdown: BigDecimal,
    val openPositions: List<Position>,
    val closedTrades: List<Trade>,
    val tradeIdCounter: Long,
    val positionIdCounter: Long,
) {
    companion object {
        fun create(initialCapital: BigDecimal): TradingState =
            TradingState(
                accountBalance = initialCapital,
                peakBalance = initialCapital,
                maxDrawdown = BigDecimal.ZERO,
                openPositions = emptyList(),
                closedTrades = emptyList(),
                tradeIdCounter = 0L,
                positionIdCounter = 0L,
            )
    }

    /**
     * Apply a single event and return the new state.
     */
    fun applyEvent(event: TradingEvent): TradingState =
        when (event) {
            is TradingEvent.PositionOpened ->
                copy(
                    openPositions = openPositions + event.position,
                    positionIdCounter = event.newPositionIdCounter,
                )

            is TradingEvent.PositionUpdated ->
                copy(
                    openPositions =
                        openPositions.map { pos ->
                            if (pos.id == event.positionId) {
                                pos.copy(
                                    trailingStop = event.newTrailingStop,
                                    highestFavorablePrice = event.newHighestFavorablePrice,
                                )
                            } else {
                                pos
                            }
                        },
                )

            is TradingEvent.PositionClosed ->
                copy(
                    openPositions = openPositions.filter { it.id != event.positionId },
                    closedTrades = closedTrades + event.trade,
                    accountBalance = event.newBalance,
                    peakBalance = event.newPeakBalance,
                    maxDrawdown = event.newMaxDrawdown,
                    tradeIdCounter = event.newTradeIdCounter,
                )
        }

    /**
     * Apply multiple events sequentially and return the final state.
     */
    fun applyEvents(events: List<TradingEvent>): TradingState = events.fold(this) { state, event -> state.applyEvent(event) }
}