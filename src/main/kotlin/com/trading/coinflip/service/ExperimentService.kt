package com.trading.coinflip.service

import com.trading.coinflip.BacktestService
import com.trading.coinflip.data.BacktestRunRepository
import com.trading.coinflip.data.ExperimentRepository
import com.trading.coinflip.data.ExperimentTradeRepository
import com.trading.coinflip.dto.*
import com.trading.coinflip.model.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

@Service
class ExperimentService(
    private val experimentRepository: ExperimentRepository,
    private val backtestRunRepository: BacktestRunRepository,
    private val experimentTradeRepository: ExperimentTradeRepository,
    private val backtestService: BacktestService
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.of("UTC"))

    @Transactional
    fun createExperiment(request: CreateExperimentRequest): ExperimentDetailDto {
        val numBacktests = request.numBacktests.coerceIn(1, 100)
        log.info { "Creating experiment for ${request.symbol} ${request.timeframe} with $numBacktests backtests" }

        val timeframe = Timeframe.fromLabel(request.timeframe)
            ?: throw IllegalArgumentException("Invalid timeframe: ${request.timeframe}")

        val startDate = Instant.parse(request.startDate)
        val endDate = Instant.parse(request.endDate)

        // Run all backtests and collect results
        val results = (1..numBacktests).map { runNumber ->
            log.info { "Running backtest $runNumber of $numBacktests" }
            backtestService.runBacktestForSymbol(
                symbol = request.symbol,
                timeframe = timeframe,
                startDate = startDate,
                endDate = endDate
            )
        }

        // Calculate aggregated statistics
        val aggregated = AggregatedStats(
            finalCapital = results.map { it.finalCapital }.averageBigDecimal(),
            totalReturn = results.map { it.totalReturn }.averageBigDecimal(),
            totalReturnPercent = results.map { it.totalReturnPercent }.averageBigDecimal(),
            maxDrawdown = results.map { it.maxDrawdown }.averageBigDecimal(),
            maxDrawdownPercent = results.map { it.maxDrawdownPercent }.averageBigDecimal(),
            winRate = results.map { it.winRate }.averageBigDecimal(),
            profitFactor = results.map { it.profitFactor }.averageBigDecimal(),
            sharpeRatio = results.map { it.sharpeRatio }.averageBigDecimal(),
            totalTrades = averageInt(results.map { it.totalTrades }).toInt(),
            winningTrades = averageInt(results.map { it.winningTrades }).toInt(),
            losingTrades = averageInt(results.map { it.losingTrades }).toInt(),
            averageWin = results.map { it.averageWin }.averageBigDecimal(),
            averageLoss = results.map { it.averageLoss }.averageBigDecimal(),
            largestWin = results.map { it.largestWin }.averageBigDecimal(),
            largestLoss = results.map { it.largestLoss }.averageBigDecimal(),
            averageTradeDuration = averageLong(results.map { it.averageTradeDuration }).toLong()
        )

        // Use first result for buy & hold (same for all runs)
        val firstResult = results.first()

        // Generate auto name
        val autoName = generateExperimentName(request.symbol, timeframe, startDate, endDate, numBacktests)

        // Create experiment entity with aggregated stats
        val experiment = Experiment(
            name = autoName,
            customName = request.customName?.takeIf { it.isNotBlank() },
            notes = request.notes?.takeIf { it.isNotBlank() },
            symbol = request.symbol,
            timeframe = timeframe,
            startDate = firstResult.startDate,
            endDate = firstResult.endDate,
            numBacktests = numBacktests,
            initialCapital = firstResult.initialCapital,
            riskPerTrade = firstResult.config.riskPerTrade,
            atrPeriod = firstResult.config.atrPeriod,
            atrMultiplier = firstResult.config.atrMultiplier,
            transactionCostPercent = firstResult.config.transactionCostPercent,
            maxConcurrentPositions = firstResult.config.maxConcurrentPositions,
            finalCapital = aggregated.finalCapital,
            totalReturn = aggregated.totalReturn,
            totalReturnPercent = aggregated.totalReturnPercent,
            maxDrawdown = aggregated.maxDrawdown,
            maxDrawdownPercent = aggregated.maxDrawdownPercent,
            winRate = aggregated.winRate,
            profitFactor = aggregated.profitFactor,
            sharpeRatio = aggregated.sharpeRatio,
            totalTrades = aggregated.totalTrades,
            winningTrades = aggregated.winningTrades,
            losingTrades = aggregated.losingTrades,
            averageWin = aggregated.averageWin,
            averageLoss = aggregated.averageLoss,
            largestWin = aggregated.largestWin,
            largestLoss = aggregated.largestLoss,
            averageTradeDuration = aggregated.averageTradeDuration,
            buyAndHoldReturn = firstResult.buyAndHoldReturn,
            buyAndHoldReturnPercent = firstResult.buyAndHoldReturnPercent
        )

        val savedExperiment = experimentRepository.save(experiment)

        // Save each backtest run and its trades
        val savedRuns = results.mapIndexed { index, result ->
            val run = BacktestRun(
                experiment = savedExperiment,
                runNumber = index + 1,
                finalCapital = result.finalCapital,
                totalReturn = result.totalReturn,
                totalReturnPercent = result.totalReturnPercent,
                maxDrawdown = result.maxDrawdown,
                maxDrawdownPercent = result.maxDrawdownPercent,
                winRate = result.winRate,
                profitFactor = result.profitFactor,
                sharpeRatio = result.sharpeRatio,
                totalTrades = result.totalTrades,
                winningTrades = result.winningTrades,
                losingTrades = result.losingTrades,
                averageWin = result.averageWin,
                averageLoss = result.averageLoss,
                largestWin = result.largestWin,
                largestLoss = result.largestLoss,
                averageTradeDuration = result.averageTradeDuration,
                buyAndHoldReturn = result.buyAndHoldReturn,
                buyAndHoldReturnPercent = result.buyAndHoldReturnPercent
            )

            val savedRun = backtestRunRepository.save(run)

            // Save trades for this run
            val trades = result.trades.mapIndexed { tradeIndex, trade ->
                ExperimentTrade(
                    backtestRun = savedRun,
                    tradeNumber = tradeIndex + 1,
                    symbol = trade.symbol,
                    timeframe = trade.timeframe,
                    side = trade.side,
                    entryTime = trade.entryTime,
                    entryPrice = trade.entryPrice,
                    exitTime = trade.exitTime,
                    exitPrice = trade.exitPrice,
                    positionSize = trade.positionSize,
                    initialStopLoss = trade.initialStopLoss,
                    trailingStop = trade.trailingStop,
                    profitLoss = trade.profitLoss,
                    profitLossPercent = trade.profitLossPercent,
                    exitReason = trade.exitReason,
                    balanceBeforeOpen = trade.balanceBeforeOpen,
                    balanceAfterOpen = trade.balanceAfterOpen,
                    balanceBeforeClose = trade.balanceBeforeClose,
                    balanceAfterClose = trade.balanceAfterClose
                )
            }

            experimentTradeRepository.saveAll(trades)
            savedRun
        }

        log.info { "Created experiment ${savedExperiment.id} with $numBacktests runs" }

        return savedExperiment.toDetailDto(savedRuns)
    }

    fun listExperiments(): List<ExperimentSummaryDto> {
        return experimentRepository.findAllByOrderByCreatedAtDesc()
            .map { it.toSummaryDto() }
    }

    fun getExperiment(id: Long): ExperimentDetailDto {
        val experiment = experimentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Experiment not found: $id") }

        val runs = backtestRunRepository.findByExperimentIdOrderByRunNumberAsc(id)

        return experiment.toDetailDto(runs)
    }

    fun getBacktestRun(runId: Long): BacktestRunDetailDto {
        val run = backtestRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Backtest run not found: $runId") }

        val trades = experimentTradeRepository.findByBacktestRunIdOrderByTradeNumberAsc(runId)

        return run.toDetailDto(trades)
    }

    fun compareExperiments(experimentIds: List<Long>): ExperimentComparisonDto {
        val experiments = experimentRepository.findByIdIn(experimentIds)

        if (experiments.size != experimentIds.size) {
            throw IllegalArgumentException("Some experiments not found")
        }

        val summaries = experiments.map { it.toSummaryDto() }

        // Build comparison metrics
        val metrics = listOf(
            buildMetric("# Backtests", experiments) { it.numBacktests.toString() },
            buildMetric("Initial Capital", experiments) { formatCurrency(it.initialCapital) },
            buildMetric("Avg Final Capital", experiments) { formatCurrency(it.finalCapital) },
            buildMetric("Avg Total Return", experiments) { formatCurrency(it.totalReturn) },
            buildMetric("Avg Total Return %", experiments) { formatPercent(it.totalReturnPercent) },
            buildMetric("Avg Max Drawdown", experiments) { formatCurrency(it.maxDrawdown) },
            buildMetric("Avg Max Drawdown %", experiments) { formatPercent(it.maxDrawdownPercent) },
            buildMetric("Avg Win Rate", experiments) { formatPercent(it.winRate) },
            buildMetric("Avg Profit Factor", experiments) { formatDecimal(it.profitFactor) },
            buildMetric("Avg Sharpe Ratio", experiments) { formatDecimal(it.sharpeRatio) },
            buildMetric("Avg Total Trades", experiments) { it.totalTrades.toString() },
            buildMetric("Avg Winning Trades", experiments) { it.winningTrades.toString() },
            buildMetric("Avg Losing Trades", experiments) { it.losingTrades.toString() },
            buildMetric("Avg Win", experiments) { formatCurrency(it.averageWin) },
            buildMetric("Avg Loss", experiments) { formatCurrency(it.averageLoss) },
            buildMetric("Avg Largest Win", experiments) { formatCurrency(it.largestWin) },
            buildMetric("Avg Largest Loss", experiments) { formatCurrency(it.largestLoss) },
            buildMetric("Buy & Hold Return %", experiments) { formatPercent(it.buyAndHoldReturnPercent) }
        )

        return ExperimentComparisonDto(
            experiments = summaries,
            metrics = metrics
        )
    }

    @Transactional
    fun deleteExperiment(id: Long) {
        if (!experimentRepository.existsById(id)) {
            throw IllegalArgumentException("Experiment not found: $id")
        }
        experimentRepository.deleteById(id)
        log.info { "Deleted experiment $id" }
    }

    private fun generateExperimentName(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
        endDate: Instant,
        numBacktests: Int
    ): String {
        val start = dateFormatter.format(startDate)
        val end = dateFormatter.format(endDate)
        return "${symbol}_${timeframe.label}_${start}_to_${end}_x$numBacktests"
    }

    private fun buildMetric(
        label: String,
        experiments: List<Experiment>,
        extractor: (Experiment) -> String
    ): ComparisonMetricDto {
        return ComparisonMetricDto(
            label = label,
            values = experiments.associate { it.id!! to extractor(it) }
        )
    }

    private fun formatCurrency(value: BigDecimal): String =
        "$${String.format("%,.2f", value)}"

    private fun formatPercent(value: BigDecimal): String =
        "${String.format("%.2f", value)}%"

    private fun formatDecimal(value: BigDecimal): String =
        String.format("%.4f", value)

    // Helper to calculate average of BigDecimal list
    private fun List<BigDecimal>.averageBigDecimal(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return this.reduce { acc, value -> acc + value }
            .divide(BigDecimal(size), 8, RoundingMode.HALF_UP)
    }

    // Helper to calculate average of Int list
    private fun averageInt(list: List<Int>): Double {
        if (list.isEmpty()) return 0.0
        return list.sum().toDouble() / list.size
    }

    // Helper to calculate average of Long list
    private fun averageLong(list: List<Long>): Double {
        if (list.isEmpty()) return 0.0
        return list.sum().toDouble() / list.size
    }

    private data class AggregatedStats(
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
        val averageTradeDuration: Long
    )
}
