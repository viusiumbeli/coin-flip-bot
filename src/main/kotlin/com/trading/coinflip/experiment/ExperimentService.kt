package com.trading.coinflip.experiment

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.ExperimentEntity
import com.trading.coinflip.common.model.ExperimentStatus
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class ExperimentService(
    private val experimentRepository: ExperimentRepository,
    private val backtestRunRepository: BacktestRunRepository,
    private val experimentTradeRepository: ExperimentTradeRepository,
    private val asyncExperimentExecutor: AsyncExperimentExecutor,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    private val dateFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("UTC"))

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
            setOf(
                "runNumber",
                "totalReturnPercent",
                "winRate",
                "sharpeRatio",
                "profitFactor",
                "maxDrawdownPercent",
                "totalTrades",
            )
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
    fun initiateExperiment(request: CreateExperimentRequest): ExperimentEntity {
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
            ExperimentEntity(
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
        experiments: List<ExperimentEntity>,
        extractor: (ExperimentEntity) -> String,
    ): ComparisonMetricDto =
        ComparisonMetricDto(
            label = label,
            values = experiments.associate { it.id!! to extractor(it) },
        )

    private fun formatCurrency(value: BigDecimal): String = "$${String.format("%,.2f", value)}"

    private fun formatPercent(value: BigDecimal): String = "${String.format("%.2f", value)}%"

    private fun formatDecimal(value: BigDecimal): String = String.format("%.4f", value)
}
