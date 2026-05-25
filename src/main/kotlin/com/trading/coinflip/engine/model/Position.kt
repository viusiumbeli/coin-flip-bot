package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Timeframe
import java.math.BigDecimal
import java.math.RoundingMode
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
                pnl.divide(positionValue, 8, RoundingMode.HALF_UP) * BigDecimal(100)
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
