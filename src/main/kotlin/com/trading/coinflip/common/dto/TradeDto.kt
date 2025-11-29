package com.trading.coinflip.common.dto

import com.trading.coinflip.engine.model.PositionSide
import java.math.BigDecimal
import java.time.Instant

data class TradeDto(
    val id: Long,
    val symbol: String,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val exitTime: Instant,
    val exitPrice: BigDecimal,
    val positionSize: BigDecimal,
    val profitLoss: BigDecimal,
    val profitLossPercent: BigDecimal,
    val exitReason: String,
    val balanceBeforeOpen: BigDecimal,
    val balanceAfterOpen: BigDecimal,
    val balanceBeforeClose: BigDecimal,
    val balanceAfterClose: BigDecimal,
)
