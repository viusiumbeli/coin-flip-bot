package com.trading.coinflip.simulation

import com.trading.coinflip.engine.model.PositionSide
import java.math.BigDecimal
import java.time.Instant

data class OpenPositionDto(
    val id: Long,
    val symbol: String,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val positionSize: BigDecimal,
    val initialStopLoss: BigDecimal,
    val trailingStop: BigDecimal,
    val unrealizedPnL: BigDecimal,
    val unrealizedPnLPercent: BigDecimal,
    val allocatedCapital: BigDecimal,
)
