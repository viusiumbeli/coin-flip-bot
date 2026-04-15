package com.trading.coinflip.experiment

import com.trading.coinflip.backtest.BacktestService
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.BacktestRun
import com.trading.coinflip.common.model.Experiment
import com.trading.coinflip.common.model.ExperimentStatus
import com.trading.coinflip.common.model.ExperimentTrade
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.data.BacktestRunRepository
import com.trading.coinflip.data.ExperimentRepository
import com.trading.coinflip.data.ExperimentTradeRepository
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

@Service
class ExperimentService(
    private val experimentRepository: ExperimentRepository,
    private val backtestRunRepository: BacktestRunRepository,
    private val experimentTradeRepository: ExperimentTradeRepository,
    private val backtestService: BacktestService,
    private val asyncExperimentExecutor: AsyncExperimentExecutor,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    private val dateFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("UTC"))

    @Transactional
    fun createExperiment(request: CreateExperimentRequest): ExperimentDetailDto {
        val numBacktests = request.numBacktests.coerceIn(1, properties.experiment.syncBacktestLimit)
        log.info { "Creating experiment for ${request.symbol} ${request.timeframe} with $numBacktests backtests" }

        val timeframe =
            Timeframe.fromLabel(request.timeframe)
                ?: throw IllegalArgumentException("Invalid timeframe: ${request.timeframe}")

        val startDate = Instant.parse(request.startDate)
        val endDate = Instant.parse(request.endDate)

        // Run all backtests and collect results
        val results =
            (1..numBacktests).map { runNumber ->
                log.info { "Running backtest $runNumber of $numBacktests" }
                backtestService.runBacktestForSymbol(
                    symbol = request.symbol,
                    timeframe = timeframe,
                    startDate = startDate,
                    endDate = endDate,
                )
            }

        // Calculate aggregated statistics
        val aggregated =
            AggregatedStats(
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
                averageTradeDuration = averageLong(results.map { it.averageTradeDuration }).toLong(),
                runsBeatBuyHold = results.count { it.totalReturnPercent >= it.buyAndHoldReturnPercent },
            )

        // Calculate variance metrics for totalReturnPercent
        val returnValues = results.map { it.totalReturnPercent.toDouble() }
        val varianceMetrics = calculateVarianceMetrics(returnValues)

        // Use first result for buy & hold (same for all runs)
        val firstResult = results.first()

        // Generate auto name
        val autoName = generateExperimentName(request.symbol, timeframe, startDate, endDate, numBacktests)

        // Create experiment entity with aggregated stats
        val experiment =
            Experiment(
                name = autoName,
                customName = request.customName?.takeIf { it.isNotBlank() },
                notes = request.notes?.takeIf { it.isNotBlank() },
                symbol = request.symbol,
                timeframe = timeframe,
                startDate = firstResult.startDate,
                endDate = firstResult.endDate,
                numBacktests = numBacktests,
                initialCapital = firstResult.initialCapital,
                riskPerTrade = firstResult.config.trading.riskPerTrade,
                atrPeriod = firstResult.config.trading.atrPeriod,
                atrMultiplier = firstResult.config.trading.atrMultiplier,
                transactionCostPercent = firstResult.config.trading.transactionCostPercent,
                maxConcurrentPositions = firstResult.config.trading.maxConcurrentPositions,
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
                buyAndHoldReturnPercent = firstResult.buyAndHoldReturnPercent,
                runsBeatBuyHold = aggregated.runsBeatBuyHold,
                // Variance metrics
                returnStdDev = varianceMetrics.stdDev,
                returnMin = varianceMetrics.min,
                returnMax = varianceMetrics.max,
                returnP5 = varianceMetrics.p5,
                returnP25 = varianceMetrics.p25,
                returnP50 = varianceMetrics.p50,
                returnP75 = varianceMetrics.p75,
                returnP95 = varianceMetrics.p95,
            )

        val savedExperiment = experimentRepository.save(experiment)

        // Save each backtest run and its trades
        val savedRuns =
            results.mapIndexed { index, result ->
                val run =
                    BacktestRun(
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
                        buyAndHoldReturnPercent = result.buyAndHoldReturnPercent,
                    )

                val savedRun = backtestRunRepository.save(run)

                // Save trades for this run
                val trades =
                    result.trades.mapIndexed { tradeIndex, trade ->
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
                            balanceAfterClose = trade.balanceAfterClose,
                        )
                    }

                experimentTradeRepository.saveAll(trades)
                savedRun
            }

        log.info { "Created experiment ${savedExperiment.id} with $numBacktests runs" }

        return savedExperiment.toDetailDto(savedRuns)
    }

    fun listExperiments(): List<ExperimentSummaryDto> =
        experimentRepository
            .findAllByOrderByCreatedAtDesc()
            .map { it.toSummaryDto() }

    fun getExperiment(id: Long): ExperimentDetailDto {
        val experiment =
            experimentRepository
                .findById(id)
                .orElseThrow { IllegalArgumentException("Experiment not found: $id") }

        // Don't load all runs - frontend will fetch them via paginated endpoint
        return experiment.toDetailDto(emptyList())
    }

    fun getExperimentRuns(
        id: Long,
        page: Int,
        size: Int,
        sortBy: String,
        sortDir: String,
    ): PaginatedRunsDto {
        if (!experimentRepository.existsById(id)) {
            throw IllegalArgumentException("Experiment not found: $id")
        }

        // Validate sortBy field to prevent invalid column names
        val allowedFields =
            setOf("runNumber", "totalReturnPercent", "winRate", "sharpeRatio", "profitFactor", "maxDrawdownPercent", "totalTrades")
        val validSortBy = if (sortBy in allowedFields) sortBy else "runNumber"

        val sort =
            Sort.by(
                if (sortDir.equals("desc", ignoreCase = true)) Sort.Direction.DESC else Sort.Direction.ASC,
                validSortBy,
            )
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, properties.api.maxPageSize), sort)
        val runsPage = backtestRunRepository.findByExperimentId(id, pageable)

        return PaginatedRunsDto(
            runs = runsPage.content.map { it.toSummaryDto() },
            page = runsPage.number,
            size = runsPage.size,
            totalPages = runsPage.totalPages,
            totalElements = runsPage.totalElements,
        )
    }

    fun getBacktestRun(runId: Long): BacktestRunDetailDto {
        val run =
            backtestRunRepository
                .findById(runId)
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
        val metrics =
            listOf(
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
                buildMetric("Buy & Hold Return %", experiments) { formatPercent(it.buyAndHoldReturnPercent) },
                // Variance metrics
                buildMetric("Return Std Dev", experiments) { it.returnStdDev?.let { formatPercent(it) } ?: "N/A" },
                buildMetric("Return Range", experiments) {
                    val min = it.returnMin?.let { formatPercent(it) } ?: "N/A"
                    val max = it.returnMax?.let { formatPercent(it) } ?: "N/A"
                    "$min to $max"
                },
            )

        return ExperimentComparisonDto(
            experiments = summaries,
            metrics = metrics,
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

    // ==================== Async Experiment Methods ====================

    /**
     * Initiates an experiment asynchronously.
     * Creates the experiment record with PENDING status and triggers background execution.
     * Returns immediately without waiting for backtests to complete.
     */
    @Transactional
    fun initiateExperiment(request: CreateExperimentRequest): Experiment {
        val numBacktests = request.numBacktests.coerceIn(1, properties.experiment.asyncBacktestLimit)
        log.info { "Initiating async experiment for ${request.symbol} ${request.timeframe} with $numBacktests backtests" }

        val timeframe =
            Timeframe.fromLabel(request.timeframe)
                ?: throw IllegalArgumentException("Invalid timeframe: ${request.timeframe}")

        val startDate = Instant.parse(request.startDate)
        val endDate = Instant.parse(request.endDate)

        // Generate auto name
        val autoName = generateExperimentName(request.symbol, timeframe, startDate, endDate, numBacktests)

        // Create experiment with PENDING status and placeholder values for aggregated stats
        val experiment =
            Experiment(
                name = autoName,
                customName = request.customName?.takeIf { it.isNotBlank() },
                notes = request.notes?.takeIf { it.isNotBlank() },
                symbol = request.symbol,
                timeframe = timeframe,
                startDate = startDate,
                endDate = endDate,
                numBacktests = numBacktests,
                initialCapital = properties.initialCapital,
                riskPerTrade = properties.trading.riskPerTrade,
                atrPeriod = properties.trading.atrPeriod,
                atrMultiplier = properties.trading.atrMultiplier,
                transactionCostPercent = properties.trading.transactionCostPercent,
                maxConcurrentPositions = properties.trading.maxConcurrentPositions,
                // Placeholder values - will be updated when experiment completes
                finalCapital = BigDecimal.ZERO,
                totalReturn = BigDecimal.ZERO,
                totalReturnPercent = BigDecimal.ZERO,
                maxDrawdown = BigDecimal.ZERO,
                maxDrawdownPercent = BigDecimal.ZERO,
                winRate = BigDecimal.ZERO,
                profitFactor = BigDecimal.ZERO,
                sharpeRatio = BigDecimal.ZERO,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                averageWin = BigDecimal.ZERO,
                averageLoss = BigDecimal.ZERO,
                largestWin = BigDecimal.ZERO,
                largestLoss = BigDecimal.ZERO,
                averageTradeDuration = 0,
                buyAndHoldReturn = BigDecimal.ZERO,
                buyAndHoldReturnPercent = BigDecimal.ZERO,
                // Status fields
                status = ExperimentStatus.PENDING,
                completedRuns = 0,
                failedRuns = 0,
                startedAt = null,
                finishedAt = null,
                errorMessage = null,
            )

        val savedExperiment = experimentRepository.save(experiment)
        log.info { "Created experiment ${savedExperiment.id} with PENDING status" }

        // Trigger async execution (non-blocking)
        asyncExperimentExecutor.executeExperiment(savedExperiment.id!!, request)

        return savedExperiment
    }

    /**
     * Gets the current status of an experiment.
     */
    fun getExperimentStatus(id: Long): ExperimentStatusDto {
        val experiment =
            experimentRepository
                .findById(id)
                .orElseThrow { IllegalArgumentException("Experiment not found: $id") }

        return ExperimentStatusDto(
            id = experiment.id!!,
            status = experiment.status,
            totalRuns = experiment.numBacktests,
            completedRuns = experiment.completedRuns,
            failedRuns = experiment.failedRuns,
            progressPercent =
                if (experiment.numBacktests > 0) {
                    (experiment.completedRuns.toDouble() / experiment.numBacktests) * 100
                } else {
                    0.0
                },
            startedAt = experiment.startedAt,
            finishedAt = experiment.finishedAt,
            errorMessage = experiment.errorMessage,
        )
    }

    /**
     * Cancels a running experiment.
     */
    fun cancelExperiment(id: Long): ExperimentStatusDto {
        val experiment =
            experimentRepository
                .findById(id)
                .orElseThrow { IllegalArgumentException("Experiment not found: $id") }

        if (experiment.status != ExperimentStatus.RUNNING && experiment.status != ExperimentStatus.PENDING) {
            throw IllegalArgumentException("Cannot cancel experiment in ${experiment.status} status")
        }

        asyncExperimentExecutor.cancel(id)

        // Return updated status
        return getExperimentStatus(id)
    }

    private fun generateExperimentName(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
        endDate: Instant,
        numBacktests: Int,
    ): String {
        val start = dateFormatter.format(startDate)
        val end = dateFormatter.format(endDate)
        return "${symbol}_${timeframe.label}_${start}_to_${end}_x$numBacktests"
    }

    private fun buildMetric(
        label: String,
        experiments: List<Experiment>,
        extractor: (Experiment) -> String,
    ): ComparisonMetricDto =
        ComparisonMetricDto(
            label = label,
            values = experiments.associate { it.id!! to extractor(it) },
        )

    private fun formatCurrency(value: BigDecimal): String = "$${String.format("%,.2f", value)}"

    private fun formatPercent(value: BigDecimal): String = "${String.format("%.2f", value)}%"

    private fun formatDecimal(value: BigDecimal): String = String.format("%.4f", value)

    // Helper to calculate average of BigDecimal list
    private fun List<BigDecimal>.averageBigDecimal(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return this
            .reduce { acc, value -> acc + value }
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
        val averageTradeDuration: Long,
        val runsBeatBuyHold: Int,
    )

    private data class VarianceMetrics(
        val stdDev: BigDecimal,
        val min: BigDecimal,
        val max: BigDecimal,
        val p5: BigDecimal,
        val p25: BigDecimal,
        val p50: BigDecimal,
        val p75: BigDecimal,
        val p95: BigDecimal,
    )

    private fun calculateVarianceMetrics(values: List<Double>): VarianceMetrics {
        if (values.isEmpty()) {
            return VarianceMetrics(
                stdDev = BigDecimal.ZERO,
                min = BigDecimal.ZERO,
                max = BigDecimal.ZERO,
                p5 = BigDecimal.ZERO,
                p25 = BigDecimal.ZERO,
                p50 = BigDecimal.ZERO,
                p75 = BigDecimal.ZERO,
                p95 = BigDecimal.ZERO,
            )
        }

        val sorted = values.sorted()
        val n = sorted.size

        // Standard deviation (sample)
        val mean = sorted.average()
        val variance =
            if (n > 1) {
                sorted.sumOf { (it - mean) * (it - mean) } / (n - 1)
            } else {
                0.0
            }
        val stdDev = sqrt(variance)

        return VarianceMetrics(
            stdDev = BigDecimal(stdDev).setScale(8, RoundingMode.HALF_UP),
            min = BigDecimal(sorted.first()).setScale(8, RoundingMode.HALF_UP),
            max = BigDecimal(sorted.last()).setScale(8, RoundingMode.HALF_UP),
            p5 = percentile(sorted, 0.05),
            p25 = percentile(sorted, 0.25),
            p50 = percentile(sorted, 0.50),
            p75 = percentile(sorted, 0.75),
            p95 = percentile(sorted, 0.95),
        )
    }

    private fun percentile(
        sortedValues: List<Double>,
        percentile: Double,
    ): BigDecimal {
        if (sortedValues.isEmpty()) return BigDecimal.ZERO
        val index = (percentile * (sortedValues.size - 1)).toInt()
        return BigDecimal(sortedValues[index.coerceIn(0, sortedValues.size - 1)])
            .setScale(8, RoundingMode.HALF_UP)
    }
}
