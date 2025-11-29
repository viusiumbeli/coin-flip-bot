package com.trading.coinflip.experiment

import com.trading.coinflip.common.model.PositionSide
import java.math.BigDecimal
import java.time.Instant

data class ExperimentTradeDto(
    val id: Long,
    val tradeNumber: Int,
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
