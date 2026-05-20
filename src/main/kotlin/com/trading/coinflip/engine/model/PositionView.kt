package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.common.model.TrailingStopMode
import com.trading.coinflip.engine.CoinFlipStrategy
import java.math.BigDecimal
import java.time.Instant

/**
 * Read-only view of a trading position.
 * Both immutable Position and mutable MutablePosition implement this interface.
 */
interface PositionView {
    val id: Long
    val symbol: String
    val timeframe: Timeframe
    val side: PositionSide
    val entryTime: Instant
    val entryPrice: BigDecimal
    val positionSize: BigDecimal
    val initialStopLoss: BigDecimal
    val trailingStop: BigDecimal
    val highestFavorablePrice: BigDecimal
    val status: PositionStatus
    val balanceBeforeOpen: BigDecimal
    val balanceAfterOpen: BigDecimal
    val allocatedCapital: BigDecimal
    val exitTime: Instant?
    val exitPrice: BigDecimal?
    val exitReason: String?

    /**
     * Calculate new trailing stop values if an update is needed.
     * Returns null if no update is required.
     */
    fun calculateTrailingStopUpdate(
        currentPrice: BigDecimal,
        atr: BigDecimal,
        trailingStopMode: TrailingStopMode,
        atrMultiplier: BigDecimal,
        trailingStopPercent: BigDecimal,
    ): TrailingStopUpdate? {
        val isFavorableMove =
            when (side) {
                PositionSide.LONG -> currentPrice > highestFavorablePrice
                PositionSide.SHORT -> currentPrice < highestFavorablePrice
            }

        if (!isFavorableMove) {
            return null
        }

        val newHighest = currentPrice

        // Calculate stop distance based on mode
        val stopDistance =
            CoinFlipStrategy.calculateStopDistance(
                currentPrice,
                atr,
                trailingStopMode,
                atrMultiplier,
                trailingStopPercent,
            )

        val newStop =
            when (side) {
                PositionSide.LONG -> currentPrice - stopDistance
                PositionSide.SHORT -> currentPrice + stopDistance
            }

        val shouldUpdate =
            when (side) {
                PositionSide.LONG -> newStop > trailingStop
                PositionSide.SHORT -> newStop < trailingStop
            }

        return if (shouldUpdate) {
            TrailingStopUpdate(newStop, newHighest)
        } else {
            // Still update highest favorable price even if stop doesn't move
            TrailingStopUpdate(trailingStop, newHighest)
        }
    }

    fun isStopHit(currentPrice: BigDecimal): Boolean =
        when (side) {
            PositionSide.LONG -> currentPrice <= trailingStop
            PositionSide.SHORT -> currentPrice >= trailingStop
        }
}
