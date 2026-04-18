package com.trading.coinflip.live.model

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.model.PositionSide
import java.math.BigDecimal
import java.time.Instant

data class LivePositionDto(
    val id: Long,
    val positionId: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val positionSize: BigDecimal,
    val initialStopLoss: BigDecimal,
    val trailingStop: BigDecimal,
    val highestFavorablePrice: BigDecimal,
    val allocatedCapital: BigDecimal,
    val unrealizedPnl: BigDecimal?,
    val unrealizedPnlPercent: BigDecimal?,
    val currentPrice: BigDecimal?,
)
