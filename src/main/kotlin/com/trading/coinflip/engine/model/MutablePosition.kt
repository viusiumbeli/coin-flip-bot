package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Timeframe
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
) : PositionView

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
