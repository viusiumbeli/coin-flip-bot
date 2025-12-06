package com.trading.coinflip.experiment

import com.trading.coinflip.api.exception.BadRequestException
import com.trading.coinflip.api.exception.NotFoundException
import com.trading.coinflip.api.experiment.ComparisonMetricResponse
import com.trading.coinflip.api.experiment.ExperimentComparisonResponse
import com.trading.coinflip.api.experiment.ExperimentStatusResponse
import com.trading.coinflip.api.experiment.ExperimentSummaryResponse
import com.trading.coinflip.backtest.BacktestRunRepository
import com.trading.coinflip.backtest.model.BacktestRunDetailDto
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.experiment.model.ExperimentEntity
import com.trading.coinflip.experiment.model.ExperimentStatus
import com.trading.coinflip.experiment.model.toExperimentDetailResponse
import com.trading.coinflip.experiment.model.toExperimentSummaryResponse
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class ExperimentService(
    private val experimentRepository: ExperimentRepository,
    private val backtestRunRepository: BacktestRunRepository,
    private val asyncExperimentExecutor: AsyncExperimentExecutor,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    private val dateFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("UTC"))

    suspend fun listExperiments(): List<ExperimentSummaryResponse> =
        experimentRepository
            .findAllByOrderByCreatedAtDesc()
            .toList()
            .map { it.toExperimentSummaryResponse() }

    suspend fun getExperiment(id: Long): ExperimentDetailResponse {
        val experiment =
            experimentRepository.findById(id)
                ?: throw NotFoundException("Experiment not found: $id")

        // Don't load all runs - frontend will fetch them via paginated endpoint
        return experiment.toExperimentDetailResponse(emptyList())
    }

    suspend fun getExperimentRuns(
        id: Long,
        page: Int,
        size: Int,
    ): PaginatedRunsDto {
        if (!experimentRepository.existsById(id)) {
            throw NotFoundException("Experiment not found: $id")
        }

        // R2DBC doesn't support Pageable - use manual pagination
        // Note: sortBy/sortDir would need to be in the SQL query for proper sorting
        // For now, just use run_number ordering from the paginated query
        val validSize = size.coerceIn(1, properties.api.maxPageSize)
        val offset = page.coerceAtLeast(0).toLong() * validSize

        val runs = backtestRunRepository.findByExperimentIdPaginated(id, validSize, offset).toList()
        val totalElements = backtestRunRepository.countByExperimentId(id)
        val totalPages = ((totalElements + validSize - 1) / validSize).toInt()

        return PaginatedRunsDto(
            runs = runs.map { it.toSummaryDto() },
            page = page.coerceAtLeast(0),
            size = validSize,
            totalPages = totalPages,
            totalElements = totalElements,
        )
    }

    suspend fun getBacktestRun(runId: Long): BacktestRunDetailDto {
        val run =
            backtestRunRepository.findById(runId)
                ?: throw NotFoundException("Backtest run not found: $runId")

        return run.toDetailDto()
    }

    suspend fun compareExperiments(experimentIds: List<Long>): ExperimentComparisonResponse {
        val experiments = experimentRepository.findByIdIn(experimentIds).toList()

        if (experiments.size != experimentIds.size) {
            throw NotFoundException("Some experiments not found")
        }

        val summaries = experiments.map { it.toExperimentSummaryResponse() }

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

        return ExperimentComparisonResponse(
            experiments = summaries,
            metrics = metrics,
        )
    }

    suspend fun deleteExperiment(id: Long) {
        experimentRepository.deleteExperimentById(id)
        log.info { "Deleted experiment $id" }
    }

    // ==================== Async Experiment Methods ====================

    /**
     * Initiates an experiment asynchronously.
     * Creates the experiment record with PENDING status and triggers background execution.
     * Returns immediately without waiting for backtests to complete.
     */
    suspend fun initiateExperiment(request: CreateExperimentRequest): ExperimentEntity {
        val numBacktests = request.numBacktests.coerceIn(1, properties.experiment.asyncBacktestLimit)
        log.info { "Initiating async experiment for ${request.symbol} ${request.timeframe.label} with $numBacktests backtests" }

        // Generate auto name
        val autoName =
            generateExperimentName(request.symbol, request.timeframe, request.startDate, request.endDate, numBacktests)

        // Create experiment with PENDING status and placeholder values for aggregated stats
        val experiment =
            ExperimentEntity(
                name = autoName,
                customName = request.customName?.takeIf { it.isNotBlank() },
                notes = request.notes?.takeIf { it.isNotBlank() },
                symbol = request.symbol,
                timeframe = request.timeframe,
                startDate = request.startDate,
                endDate = request.endDate,
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
    suspend fun getExperimentStatus(id: Long): ExperimentStatusResponse {
        val experiment =
            experimentRepository.findById(id)
                ?: throw NotFoundException("Experiment not found: $id")

        return ExperimentStatusResponse(
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
    suspend fun cancelExperiment(id: Long): ExperimentStatusResponse {
        val experiment =
            experimentRepository.findById(id)
                ?: throw NotFoundException("Experiment not found: $id")

        if (experiment.status != ExperimentStatus.RUNNING && experiment.status != ExperimentStatus.PENDING) {
            throw BadRequestException("Cannot cancel experiment in ${experiment.status} status")
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
    ): ComparisonMetricResponse =
        ComparisonMetricResponse(
            label = label,
            values = experiments.associate { it.id!! to extractor(it) },
        )

    private fun formatCurrency(value: BigDecimal): String = "$${String.format("%,.2f", value)}"

    private fun formatPercent(value: BigDecimal): String = "${String.format("%.2f", value)}%"

    private fun formatDecimal(value: BigDecimal): String = String.format("%.4f", value)
}
