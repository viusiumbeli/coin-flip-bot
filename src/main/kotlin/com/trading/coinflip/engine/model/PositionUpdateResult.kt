package com.trading.coinflip.engine.model

import java.math.BigDecimal
import java.time.Instant

/**
 * Result of evaluating a position against current candle.
 */
sealed class PositionUpdateResult {
    /**
     * Trailing stop was updated but position remains open.
     */
    data class Updated(
        val newTrailingStop: BigDecimal,
        val newHighestFavorablePrice: BigDecimal,
    ) : PositionUpdateResult()

    /**
     * Stop was hit - position should be closed.
     */
    data class StopHit(
        val exitPrice: BigDecimal,
        val exitTime: Instant,
        val exitReason: String,
        val newTrailingStop: BigDecimal,
        val newHighestFavorablePrice: BigDecimal,
    ) : PositionUpdateResult()

    /**
     * No changes needed.
     */
    data object NoChange : PositionUpdateResult()
}
