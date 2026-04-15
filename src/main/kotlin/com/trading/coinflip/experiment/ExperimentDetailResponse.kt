package com.trading.coinflip.experiment

import com.trading.coinflip.common.dto.BacktestRunSummaryDto
import java.math.BigDecimal
import java.time.Instant

data class ExperimentDetailResponse(
    val id: Long,
    val name: String,
    val customName: String?,
    val notes: String?,
    val symbol: String,
    val timeframe: String,
    val startDate: Instant,
    val endDate: Instant,
    val createdAt: Instant,
    val numBacktests: Int,
    // Configuration
    val initialCapital: BigDecimal,
    val riskPerTrade: BigDecimal,
    val atrPeriod: Int,
    val atrMultiplier: BigDecimal,
    val transactionCostPercent: BigDecimal,
    val maxConcurrentPositions: Int,
    // Aggregated Results (averages across all runs)
    val finalCapital: BigDecimal,
    val totalReturn: BigDecimal,
    val totalReturnPercent: BigDecimal,
    val maxDrawdown: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val winRate: BigDecimal,
    val profitFactor: BigDecimal,
    val sharpeRatio: BigDecimal,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val averageWin: BigDecimal,
    val averageLoss: BigDecimal,
    val largestWin: BigDecimal,
    val largestLoss: BigDecimal,
    val averageTradeDuration: Long,
    val buyAndHoldReturn: BigDecimal,
    val buyAndHoldReturnPercent: BigDecimal,
    val runsBeatBuyHold: Int,
    // Variance/distribution metrics for totalReturnPercent
    val returnStdDev: BigDecimal?,
    val returnMin: BigDecimal?,
    val returnMax: BigDecimal?,
    val returnP5: BigDecimal?,
    val returnP25: BigDecimal?,
    val returnP50: BigDecimal?,
    val returnP75: BigDecimal?,
    val returnP95: BigDecimal?,
    // Individual runs
    val runs: List<BacktestRunSummaryDto>,
)
