package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.common.model.Trade
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
 */
data class Position(
    val id: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val positionSize: BigDecimal,
    val initialStopLoss: BigDecimal,
    val trailingStop: BigDecimal,
    val highestFavorablePrice: BigDecimal,
    val status: PositionStatus,
    val balanceBeforeOpen: BigDecimal,
    val balanceAfterOpen: BigDecimal,
    val allocatedCapital: BigDecimal,
    val exitTime: Instant? = null,
    val exitPrice: BigDecimal? = null,
    val exitReason: String? = null,
) {
    /**
     * Calculate new trailing stop values if an update is needed.
     * Returns null if no update is required.
     */
    fun calculateTrailingStopUpdate(
        currentPrice: BigDecimal,
        atr: BigDecimal,
        atrMultiplier: BigDecimal,
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
        val newStop =
            when (side) {
                PositionSide.LONG -> currentPrice - (atr * atrMultiplier)
                PositionSide.SHORT -> currentPrice + (atr * atrMultiplier)
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

    fun toTrade(
        tradeId: Long,
        balanceBeforeClose: BigDecimal,
        balanceAfterClose: BigDecimal,
    ): Trade {
        require(status == PositionStatus.CLOSED) { "Position must be closed to convert to trade" }

        val finalExitPrice = checkNotNull(exitPrice) { "Exit price must be set" }
        val finalExitTime = checkNotNull(exitTime) { "Exit time must be set" }

        val pnl =
            when (side) {
                PositionSide.LONG -> (finalExitPrice - entryPrice) * positionSize
                PositionSide.SHORT -> (entryPrice - finalExitPrice) * positionSize
            }

        val positionValue = entryPrice * positionSize
        val pnlPercent =
            if (positionValue > BigDecimal.ZERO) {
                (pnl / positionValue) * BigDecimal(100)
            } else {
                BigDecimal.ZERO
            }

        return Trade(
            id = tradeId,
            symbol = symbol,
            timeframe = timeframe,
            side = side,
            entryTime = entryTime,
            entryPrice = entryPrice,
            exitTime = finalExitTime,
            exitPrice = finalExitPrice,
            positionSize = positionSize,
            initialStopLoss = initialStopLoss,
            trailingStop = trailingStop,
            profitLoss = pnl,
            profitLossPercent = pnlPercent,
            exitReason = exitReason ?: "Unknown",
            balanceBeforeOpen = balanceBeforeOpen,
            balanceAfterOpen = balanceAfterOpen,
            balanceBeforeClose = balanceBeforeClose,
            balanceAfterClose = balanceAfterClose,
        )
    }
}
