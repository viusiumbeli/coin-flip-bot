package com.trading.coinflip.experiment

import com.trading.coinflip.common.model.ExperimentStatus
import java.math.BigDecimal
import java.time.Instant

data class ExperimentSummaryDto(
    val id: Long,
    val name: String,
    val customName: String?,
    val symbol: String,
    val timeframe: String,
    val startDate: Instant,
    val endDate: Instant,
    val createdAt: Instant,
    val numBacktests: Int,
    val totalTrades: Int,
    val totalReturnPercent: BigDecimal,
    val buyAndHoldReturnPercent: BigDecimal,
    val winRate: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val sharpeRatio: BigDecimal,
    val profitFactor: BigDecimal,
    val status: ExperimentStatus,
    val completedRuns: Int,
    val progressPercent: Double,
)
