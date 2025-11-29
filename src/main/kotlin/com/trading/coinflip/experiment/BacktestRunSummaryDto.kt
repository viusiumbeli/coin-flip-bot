package com.trading.coinflip.experiment

import java.math.BigDecimal

data class BacktestRunSummaryDto(
    val id: Long,
    val runNumber: Int,
    val totalReturnPercent: BigDecimal,
    val winRate: BigDecimal,
    val sharpeRatio: BigDecimal,
    val profitFactor: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val totalTrades: Int,
)
