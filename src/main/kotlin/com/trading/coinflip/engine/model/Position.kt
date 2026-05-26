package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Timeframe
import java.math.BigDecimal
import java.time.Instant

/**
 * Result of calculating a trailing stop update.
 */
data class TrailingStopUpdate(
    val newTrailingStop: BigDecimal,
    val newHighestFavorablePrice: BigDecimal,
)

/**
 * Immutable position data.
 * Use copy() to create modified versions.
 * Implements PositionView for compatibility with TradingProcessor.
 */
data class Position(
    override val id: Long,
    override val symbol: String,
    override val timeframe: Timeframe,
    override val side: PositionSide,
    override val entryTime: Instant,
    override val entryPrice: BigDecimal,
    override val positionSize: BigDecimal,
    override val initialStopLoss: BigDecimal,
    override val trailingStop: BigDecimal,
    override val highestFavorablePrice: BigDecimal,
    override val status: PositionStatus,
    override val balanceBeforeOpen: BigDecimal,
    override val balanceAfterOpen: BigDecimal,
    override val allocatedCapital: BigDecimal,
    override val exitTime: Instant? = null,
    override val exitPrice: BigDecimal? = null,
    override val exitReason: String? = null,
) : PositionView {
    // calculateTrailingStopUpdate and isStopHit are inherited from PositionView

}
