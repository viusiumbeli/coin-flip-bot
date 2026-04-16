package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Trade
import java.math.BigDecimal
import java.time.Instant

/**
 * Sealed class representing trading events that describe state changes.
 * TradingProcessor returns these events instead of mutating state directly.
 */
sealed class TradingEvent {
    /**
     * A new position was opened.
     */
    data class PositionOpened(
        val position: Position,
        val newPositionIdCounter: Long,
    ) : TradingEvent()

    /**
     * An existing position's trailing stop was updated.
     */
    data class PositionUpdated(
        val positionId: Long,
        val newTrailingStop: BigDecimal,
        val newHighestFavorablePrice: BigDecimal,
    ) : TradingEvent()

    /**
     * A position was closed (stop hit or forced close).
     */
    data class PositionClosed(
        val positionId: Long,
        val exitPrice: BigDecimal,
        val exitTime: Instant,
        val exitReason: String,
        val pnl: BigDecimal,
        val transactionCost: BigDecimal,
        val trade: Trade,
        val newBalance: BigDecimal,
        val newPeakBalance: BigDecimal,
        val newMaxDrawdown: BigDecimal,
        val newTradeIdCounter: Long,
    ) : TradingEvent()
}
