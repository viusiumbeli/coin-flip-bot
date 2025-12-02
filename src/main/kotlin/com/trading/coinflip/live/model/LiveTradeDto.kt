package com.trading.coinflip.live.model

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.model.PositionSide
import java.math.BigDecimal
import java.time.Instant

data class LiveTradeDto(
    val id: Long,
    val tradeId: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val exitTime: Instant,
    val exitPrice: BigDecimal,
    val positionSize: BigDecimal,
    val profitLoss: BigDecimal,
    val profitLossPercent: BigDecimal,
    val exitReason: String,
)
