package com.trading.coinflip.dto

import com.trading.coinflip.model.BacktestRun
import com.trading.coinflip.model.Experiment
import com.trading.coinflip.model.ExperimentStatus
import com.trading.coinflip.model.ExperimentTrade
import com.trading.coinflip.model.PositionSide
import java.math.BigDecimal
import java.time.Instant

// Request DTOs
data class CreateExperimentRequest(
    val symbol: String,
    val timeframe: String,
    val startDate: String,
    val endDate: String,
    val numBacktests: Int = 1,
    val customName: String? = null,
    val notes: String? = null
)

data class CompareExperimentsRequest(
    val experimentIds: List<Long>
)

// Async experiment response DTOs
data class CreateExperimentResponse(
    val id: Long,
    val status: ExperimentStatus,
    val message: String
)

data class ExperimentStatusDto(
    val id: Long,
    val status: ExperimentStatus,
    val totalRuns: Int,
    val completedRuns: Int,
    val failedRuns: Int,
    val progressPercent: Double,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val errorMessage: String?
)

data class PaginatedRunsDto(
    val runs: List<BacktestRunSummaryDto>,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val totalElements: Long
)

// Response DTOs
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
    val progressPercent: Double
)

data class BacktestRunSummaryDto(
    val id: Long,
    val runNumber: Int,
    val totalReturnPercent: BigDecimal,
    val winRate: BigDecimal,
    val sharpeRatio: BigDecimal,
    val profitFactor: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val totalTrades: Int
)

data class BacktestRunDetailDto(
    val id: Long,
    val runNumber: Int,
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
    val trades: List<ExperimentTradeDto>
)

data class ExperimentDetailDto(
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

    // Individual runs
    val runs: List<BacktestRunSummaryDto>
)

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
    val balanceAfterClose: BigDecimal
)

data class ComparisonMetricDto(
    val label: String,
    val values: Map<Long, String>
)

data class ExperimentComparisonDto(
    val experiments: List<ExperimentSummaryDto>,
    val metrics: List<ComparisonMetricDto>
)

// Extension functions
fun Experiment.toSummaryDto() = ExperimentSummaryDto(
    id = id!!,
    name = name,
    customName = customName,
    symbol = symbol,
    timeframe = timeframe.label,
    startDate = startDate,
    endDate = endDate,
    createdAt = createdAt,
    numBacktests = numBacktests,
    totalTrades = totalTrades,
    totalReturnPercent = totalReturnPercent,
    buyAndHoldReturnPercent = buyAndHoldReturnPercent,
    winRate = winRate,
    maxDrawdownPercent = maxDrawdownPercent,
    sharpeRatio = sharpeRatio,
    profitFactor = profitFactor,
    status = status,
    completedRuns = completedRuns,
    progressPercent = if (numBacktests > 0) (completedRuns.toDouble() / numBacktests) * 100 else 0.0
)

fun Experiment.toDetailDto(runs: List<BacktestRun>) = ExperimentDetailDto(
    id = id!!,
    name = name,
    customName = customName,
    notes = notes,
    symbol = symbol,
    timeframe = timeframe.label,
    startDate = startDate,
    endDate = endDate,
    createdAt = createdAt,
    numBacktests = numBacktests,
    initialCapital = initialCapital,
    riskPerTrade = riskPerTrade,
    atrPeriod = atrPeriod,
    atrMultiplier = atrMultiplier,
    transactionCostPercent = transactionCostPercent,
    maxConcurrentPositions = maxConcurrentPositions,
    finalCapital = finalCapital,
    totalReturn = totalReturn,
    totalReturnPercent = totalReturnPercent,
    maxDrawdown = maxDrawdown,
    maxDrawdownPercent = maxDrawdownPercent,
    winRate = winRate,
    profitFactor = profitFactor,
    sharpeRatio = sharpeRatio,
    totalTrades = totalTrades,
    winningTrades = winningTrades,
    losingTrades = losingTrades,
    averageWin = averageWin,
    averageLoss = averageLoss,
    largestWin = largestWin,
    largestLoss = largestLoss,
    averageTradeDuration = averageTradeDuration,
    buyAndHoldReturn = buyAndHoldReturn,
    buyAndHoldReturnPercent = buyAndHoldReturnPercent,
    runsBeatBuyHold = runsBeatBuyHold,
    runs = runs.map { it.toSummaryDto() }
)

fun BacktestRun.toSummaryDto() = BacktestRunSummaryDto(
    id = id!!,
    runNumber = runNumber,
    totalReturnPercent = totalReturnPercent,
    winRate = winRate,
    sharpeRatio = sharpeRatio,
    profitFactor = profitFactor,
    maxDrawdownPercent = maxDrawdownPercent,
    totalTrades = totalTrades
)

fun BacktestRun.toDetailDto(trades: List<ExperimentTrade>) = BacktestRunDetailDto(
    id = id!!,
    runNumber = runNumber,
    finalCapital = finalCapital,
    totalReturn = totalReturn,
    totalReturnPercent = totalReturnPercent,
    maxDrawdown = maxDrawdown,
    maxDrawdownPercent = maxDrawdownPercent,
    winRate = winRate,
    profitFactor = profitFactor,
    sharpeRatio = sharpeRatio,
    totalTrades = totalTrades,
    winningTrades = winningTrades,
    losingTrades = losingTrades,
    averageWin = averageWin,
    averageLoss = averageLoss,
    largestWin = largestWin,
    largestLoss = largestLoss,
    averageTradeDuration = averageTradeDuration,
    buyAndHoldReturn = buyAndHoldReturn,
    buyAndHoldReturnPercent = buyAndHoldReturnPercent,
    trades = trades.map { it.toDto() }
)

fun ExperimentTrade.toDto() = ExperimentTradeDto(
    id = id!!,
    tradeNumber = tradeNumber,
    symbol = symbol,
    side = side,
    entryTime = entryTime,
    entryPrice = entryPrice,
    exitTime = exitTime,
    exitPrice = exitPrice,
    positionSize = positionSize,
    profitLoss = profitLoss,
    profitLossPercent = profitLossPercent,
    exitReason = exitReason,
    balanceBeforeOpen = balanceBeforeOpen,
    balanceAfterOpen = balanceAfterOpen,
    balanceBeforeClose = balanceBeforeClose,
    balanceAfterClose = balanceAfterClose
)
