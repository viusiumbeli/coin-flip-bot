package com.trading.coinflip.common.model

import com.trading.coinflip.api.experiment.ExperimentSummaryResponse
import com.trading.coinflip.experiment.ExperimentDetailResponse
import com.trading.coinflip.experiment.toSummaryDto
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("experiments")
data class ExperimentEntity(
    @Id
    val id: Long? = null,
    val name: String,
    @Column("custom_name")
    val customName: String? = null,
    val notes: String? = null,
    val symbol: String,
    val timeframe: Timeframe,
    @Column("start_date")
    var startDate: Instant,
    @Column("end_date")
    var endDate: Instant,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("num_backtests")
    val numBacktests: Int = 1,
    // Configuration
    @Column("initial_capital")
    val initialCapital: BigDecimal,
    @Column("risk_per_trade")
    val riskPerTrade: BigDecimal,
    @Column("atr_period")
    val atrPeriod: Int,
    @Column("atr_multiplier")
    val atrMultiplier: BigDecimal,
    @Column("transaction_cost_percent")
    val transactionCostPercent: BigDecimal,
    @Column("max_concurrent_positions")
    val maxConcurrentPositions: Int,
    // Aggregated Results (averages across all runs) - mutable for async updates
    @Column("final_capital")
    var finalCapital: BigDecimal,
    @Column("total_return")
    var totalReturn: BigDecimal,
    @Column("total_return_percent")
    var totalReturnPercent: BigDecimal,
    @Column("max_drawdown")
    var maxDrawdown: BigDecimal,
    @Column("max_drawdown_percent")
    var maxDrawdownPercent: BigDecimal,
    @Column("win_rate")
    var winRate: BigDecimal,
    @Column("profit_factor")
    var profitFactor: BigDecimal,
    @Column("sharpe_ratio")
    var sharpeRatio: BigDecimal,
    @Column("total_trades")
    var totalTrades: Int,
    @Column("winning_trades")
    var winningTrades: Int,
    @Column("losing_trades")
    var losingTrades: Int,
    @Column("average_win")
    var averageWin: BigDecimal,
    @Column("average_loss")
    var averageLoss: BigDecimal,
    @Column("largest_win")
    var largestWin: BigDecimal,
    @Column("largest_loss")
    var largestLoss: BigDecimal,
    @Column("average_trade_duration")
    var averageTradeDuration: Long,
    @Column("buy_and_hold_return")
    var buyAndHoldReturn: BigDecimal,
    @Column("buy_and_hold_return_percent")
    var buyAndHoldReturnPercent: BigDecimal,
    @Column("runs_beat_buy_hold")
    var runsBeatBuyHold: Int = 0,
    // Variance/distribution metrics for totalReturnPercent (nullable for backward compatibility)
    @Column("return_std_dev")
    var returnStdDev: BigDecimal? = null,
    @Column("return_min")
    var returnMin: BigDecimal? = null,
    @Column("return_max")
    var returnMax: BigDecimal? = null,
    @Column("return_p5")
    var returnP5: BigDecimal? = null,
    @Column("return_p25")
    var returnP25: BigDecimal? = null,
    @Column("return_p50")
    var returnP50: BigDecimal? = null,
    @Column("return_p75")
    var returnP75: BigDecimal? = null,
    @Column("return_p95")
    var returnP95: BigDecimal? = null,
    // Async execution status
    var status: ExperimentStatus = ExperimentStatus.COMPLETED,
    @Column("completed_runs")
    var completedRuns: Int = 0,
    @Column("failed_runs")
    var failedRuns: Int = 0,
    @Column("started_at")
    var startedAt: Instant? = null,
    @Column("finished_at")
    var finishedAt: Instant? = null,
    @Column("error_message")
    var errorMessage: String? = null,
)

fun ExperimentEntity.toExperimentSummaryResponse() =
    ExperimentSummaryResponse(
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
        progressPercent = if (numBacktests > 0) (completedRuns.toDouble() / numBacktests) * 100 else 0.0,
    )

fun ExperimentEntity.toExperimentDetailResponse(runs: List<BacktestRunEntity>) =
    ExperimentDetailResponse(
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
        returnStdDev = returnStdDev,
        returnMin = returnMin,
        returnMax = returnMax,
        returnP5 = returnP5,
        returnP25 = returnP25,
        returnP50 = returnP50,
        returnP75 = returnP75,
        returnP95 = returnP95,
        runs = runs.map { it.toSummaryDto() },
    )
