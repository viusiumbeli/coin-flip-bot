package com.trading.coinflip.common.model

import java.math.BigDecimal
import java.time.Instant

data class Position(
    val id: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val positionSize: BigDecimal,
    val initialStopLoss: BigDecimal,
    var trailingStop: BigDecimal,
    var highestFavorablePrice: BigDecimal,
    var status: PositionStatus,
    val balanceBeforeOpen: BigDecimal,
    val balanceAfterOpen: BigDecimal,
    val allocatedCapital: BigDecimal, // Capital locked up for this position
    var exitTime: Instant? = null,
    var exitPrice: BigDecimal? = null,
    var exitReason: String? = null,
) {
    fun updateTrailingStop(
        currentPrice: BigDecimal,
        atr: BigDecimal,
        atrMultiplier: BigDecimal,
    ): Boolean {
        val isFavorableMove =
            when (side) {
                PositionSide.LONG -> currentPrice > highestFavorablePrice
                PositionSide.SHORT -> currentPrice < highestFavorablePrice
            }

        if (isFavorableMove) {
            highestFavorablePrice = currentPrice
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

            if (shouldUpdate) {
                trailingStop = newStop
                return true
            }
        }
        return false
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
        require(exitPrice != null && exitTime != null) { "Exit price and time must be set" }

        val pnl =
            when (side) {
                PositionSide.LONG -> (exitPrice!! - entryPrice) * positionSize
                PositionSide.SHORT -> (entryPrice - exitPrice!!) * positionSize
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
            exitTime = exitTime!!,
            exitPrice = exitPrice!!,
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
