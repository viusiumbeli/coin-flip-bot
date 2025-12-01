package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.common.model.Trade
import java.math.BigDecimal
import java.time.Instant

/**
 * Mutable position for high-performance backtest execution.
 * Avoids object allocation by mutating in place.
 * Implements PositionView for compatibility with TradingProcessor.
 */
class MutablePosition(
    override val id: Long,
    override val symbol: String,
    override val timeframe: Timeframe,
    override val side: PositionSide,
    override val entryTime: Instant,
    override val entryPrice: BigDecimal,
    override val positionSize: BigDecimal,
    override val initialStopLoss: BigDecimal,
    override var trailingStop: BigDecimal,
    override var highestFavorablePrice: BigDecimal,
    override var status: PositionStatus,
    override val balanceBeforeOpen: BigDecimal,
    override val balanceAfterOpen: BigDecimal,
    override val allocatedCapital: BigDecimal,
    override var exitTime: Instant? = null,
    override var exitPrice: BigDecimal? = null,
    override var exitReason: String? = null,
) : PositionView {
    companion object {
        private val HUNDRED = BigDecimal(100)
    }

    // calculateTrailingStopUpdate and isStopHit are inherited from PositionView

    /**
     * Convert to immutable Position (only used at end of backtest or for closed positions).
     */
    fun toImmutable(): Position =
        Position(
            id = id,
            symbol = symbol,
            timeframe = timeframe,
            side = side,
            entryTime = entryTime,
            entryPrice = entryPrice,
            positionSize = positionSize,
            initialStopLoss = initialStopLoss,
            trailingStop = trailingStop,
            highestFavorablePrice = highestFavorablePrice,
            status = status,
            balanceBeforeOpen = balanceBeforeOpen,
            balanceAfterOpen = balanceAfterOpen,
            allocatedCapital = allocatedCapital,
            exitTime = exitTime,
            exitPrice = exitPrice,
            exitReason = exitReason,
        )

    /**
     * Convert to Trade (used when position is closed).
     */
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
                (pnl / positionValue) * HUNDRED
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

/**
 * Convert immutable Position to MutablePosition.
 */
fun Position.toMutable(): MutablePosition =
    MutablePosition(
        id = id,
        symbol = symbol,
        timeframe = timeframe,
        side = side,
        entryTime = entryTime,
        entryPrice = entryPrice,
        positionSize = positionSize,
        initialStopLoss = initialStopLoss,
        trailingStop = trailingStop,
        highestFavorablePrice = highestFavorablePrice,
        status = status,
        balanceBeforeOpen = balanceBeforeOpen,
        balanceAfterOpen = balanceAfterOpen,
        allocatedCapital = allocatedCapital,
        exitTime = exitTime,
        exitPrice = exitPrice,
        exitReason = exitReason,
    )
