package com.trading.coinflip.experiment

import com.trading.coinflip.backtest.BacktestEngine
import com.trading.coinflip.backtest.model.BacktestConfig
import com.trading.coinflip.backtest.model.BacktestResultWithRunNumber
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.data.CandleRepository
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Async executor for running large-scale experiments using Kotlin coroutines.
 *
 * Key features:
 * - Loads candles once and shares them across all backtests (read-only)
 * - Uses semaphore-limited parallelism to prevent resource exhaustion
 * - Streams results to BatchPersistenceService via Channel
 * - Supports graceful shutdown and cancellation
 */
@Service
class AsyncExperimentExecutor(
    private val candleRepository: CandleRepository,
    private val batchPersistenceService: BatchPersistenceService,
    private val experimentRepository: ExperimentRepository,
    private val properties: BacktestProperties,
    private val backtestEngine: BacktestEngine,
) {
    private val log = KotlinLogging.logger {}

    // Coroutine scope with SupervisorJob - allows individual backtest failures without cancelling all
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track active jobs for cancellation and shutdown
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    // Limit concurrent backtests to prevent CPU/memory saturation
    private val parallelism =
        (Runtime.getRuntime().availableProcessors() * 2)
            .coerceIn(properties.async.parallelismMin, properties.async.parallelismMax)

    init {
        log.info { "AsyncExperimentExecutor initialized with parallelism=$parallelism" }
    }

    /**
     * Executes an experiment asynchronously.
     * Returns immediately after launching the coroutine.
     */
    fun executeExperiment(
        experimentId: Long,
        request: CreateExperimentRequest,
    ) {
        val job =
            scope.launch {
                try {
                    executeInternal(experimentId, request)
                } catch (e: CancellationException) {
                    log.info { "Experiment $experimentId was cancelled" }
                    experimentRepository.markExperimentCancelled(experimentId)
                    throw e
                } catch (e: Exception) {
                    log.error(e) { "Experiment $experimentId failed with error" }
                    experimentRepository.markExperimentFailed(experimentId, e.message ?: "Unknown error")
                }
            }

        activeJobs[experimentId] = job
        job.invokeOnCompletion { activeJobs.remove(experimentId) }

        log.info { "Launched async execution for experiment $experimentId" }
    }

    private suspend fun executeInternal(
        experimentId: Long,
        request: CreateExperimentRequest,
    ) {
        val numBacktests = request.numBacktests.coerceIn(1, properties.experiment.asyncBacktestLimit)

        log.info { "Starting experiment $experimentId: ${request.symbol} ${request.timeframe.label} with $numBacktests backtests" }

        // Update experiment status to RUNNING
        experimentRepository.markExperimentRunning(experimentId)

        // Step 1: Load candles from DB (data must already exist via /api/data/sync)
        log.info { "Loading candles for experiment $experimentId..." }
        val loadStartTime = System.currentTimeMillis()

        val candles =
            candleRepository
                .findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                    symbol = request.symbol,
                    timeframe = request.timeframe,
                    startTime = request.startDate,
                    endTime = request.endDate,
                ).toList()

        val loadTime = System.currentTimeMillis() - loadStartTime
        log.info { "Loaded ${candles.size} candles in ${loadTime}ms for experiment $experimentId" }

        if (candles.isEmpty()) {
            throw IllegalArgumentException("No candles available for ${request.symbol} ${request.timeframe.label}")
        }

        // Step 2: Create backtest config
        val config =
            BacktestConfig(
                symbol = request.symbol,
                timeframe = request.timeframe,
                initialCapital = properties.initialCapital,
                trading = properties.trading,
                startDate = request.startDate,
                endDate = request.endDate,
            )

        // Step 3: Create aggregator (no channel - direct persistence calls)
        val aggregator = RunningAggregator()

        // Step 4: Run backtests with yield-based rate limiting
        val semaphore = Semaphore(parallelism)
        val backtestStartTime = System.currentTimeMillis()

        log.info { "Starting $numBacktests backtests with parallelism=$parallelism for experiment $experimentId" }

        // Launch with withPermit inside (fast), yield periodically to prevent coroutine explosion
        // Workers call persistence directly - no channel overhead
        coroutineScope {
            (1..numBacktests).forEach { runNumber ->
                launch {
                    semaphore.withPermit {
                        try {
                            val result = backtestEngine.runBacktest(config, candles)
                            batchPersistenceService.submitResult(
                                experimentId,
                                BacktestResultWithRunNumber(result, runNumber),
                                aggregator,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log.warn(e) { "Backtest run $runNumber failed for experiment $experimentId" }
                            experimentRepository.incrementFailedRuns(experimentId)
                        }
                    }
                }
            }
        }

        // Step 5: Flush any remaining results
        batchPersistenceService.flushRemaining(experimentId, aggregator)

        // Step 7: Finalize experiment with aggregated statistics
        val backtestTime = System.currentTimeMillis() - backtestStartTime
        val runsPerSec = numBacktests * 1000.0 / backtestTime
        log.info {
            "Completed $numBacktests backtests in ${backtestTime}ms (${String.format(
                "%.1f",
                runsPerSec,
            )} runs/sec) for experiment $experimentId"
        }

        // Get actual dates from the candles
        val actualStartDate = candles.first().openTime
        val actualEndDate = candles.last().openTime

        batchPersistenceService.finalizeExperiment(experimentId, aggregator, actualStartDate, actualEndDate)

        log.info { "Experiment $experimentId completed successfully" }
    }

    /**
     * Cancels a running experiment.
     */
    fun cancel(experimentId: Long): Boolean {
        val job = activeJobs[experimentId]
        return if (job != null && job.isActive) {
            job.cancel()
            log.info { "Cancellation requested for experiment $experimentId" }
            true
        } else {
            log.warn { "No active job found for experiment $experimentId" }
            false
        }
    }

    /**
     * Graceful shutdown - cancel all running experiments and wait for completion.
     */
    @PreDestroy
    fun shutdown() {
        log.info { "Shutting down AsyncExperimentExecutor... (${activeJobs.size} active jobs)" }

        // Mark all running experiments as failed due to shutdown
        runBlocking {
            activeJobs.keys.forEach { experimentId ->
                experimentRepository.markExperimentFailed(experimentId, "Server shutdown")
            }
        }

        // Cancel scope and wait for jobs to complete
        scope.cancel()

        runBlocking {
            withTimeoutOrNull(properties.async.shutdownTimeoutMs) {
                activeJobs.values.joinAll()
            } ?: log.warn { "Shutdown timeout - some jobs did not complete" }
        }

        log.info { "AsyncExperimentExecutor shutdown complete" }
    }
}
