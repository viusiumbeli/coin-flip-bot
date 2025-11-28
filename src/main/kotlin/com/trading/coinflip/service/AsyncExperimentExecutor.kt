package com.trading.coinflip.service

import com.trading.coinflip.backtesting.BacktestEngine
import com.trading.coinflip.config.BacktestProperties
import com.trading.coinflip.data.DataService
import com.trading.coinflip.data.ExperimentRepository
import com.trading.coinflip.dto.CreateExperimentRequest
import com.trading.coinflip.model.BacktestConfig
import com.trading.coinflip.model.ExperimentStatus
import com.trading.coinflip.model.Timeframe
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
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
    private val dataService: DataService,
    private val batchPersistenceService: BatchPersistenceService,
    private val experimentRepository: ExperimentRepository,
    private val properties: BacktestProperties,
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
                    batchPersistenceService.markExperimentCancelled(experimentId)
                    throw e
                } catch (e: Exception) {
                    log.error(e) { "Experiment $experimentId failed with error" }
                    batchPersistenceService.markExperimentFailed(experimentId, e.message ?: "Unknown error")
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
        val timeframe =
            Timeframe.fromLabel(request.timeframe)
                ?: throw IllegalArgumentException("Invalid timeframe: ${request.timeframe}")

        val startDate = Instant.parse(request.startDate)
        val endDate = Instant.parse(request.endDate)
        val numBacktests = request.numBacktests.coerceIn(1, properties.experiment.asyncBacktestLimit)

        log.info { "Starting experiment $experimentId: ${request.symbol} ${timeframe.label} with $numBacktests backtests" }

        // Update experiment status to RUNNING
        updateExperimentStatus(experimentId, ExperimentStatus.RUNNING)

        // Step 1: Load candles ONCE (this is the key optimization)
        log.info { "Loading candles for experiment $experimentId..." }
        val loadStartTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            dataService.loadHistoricalData(
                symbol = request.symbol,
                timeframe = timeframe,
                startDate = startDate,
            )
        }

        val candles =
            withContext(Dispatchers.IO) {
                dataService.getCandlesForBacktest(
                    symbol = request.symbol,
                    timeframe = timeframe,
                    startDate = startDate,
                    endDate = endDate,
                )
            }

        val loadTime = System.currentTimeMillis() - loadStartTime
        log.info { "Loaded ${candles.size} candles in ${loadTime}ms for experiment $experimentId" }

        if (candles.isEmpty()) {
            throw IllegalArgumentException("No candles available for ${request.symbol} ${timeframe.label}")
        }

        // Step 2: Create backtest config
        val config =
            BacktestConfig(
                symbol = request.symbol,
                timeframe = timeframe,
                initialCapital = properties.initialCapital,
                trading = properties.trading,
                startDate = startDate,
                endDate = endDate,
            )

        // Step 3: Create result channel and aggregator
        val resultChannel = Channel<BacktestResultWithRunNumber>(capacity = properties.async.channelCapacity)
        val aggregator = RunningAggregator()

        // Step 4: Run backtests with semaphore-limited parallelism
        val semaphore = Semaphore(parallelism)
        val backtestStartTime = System.currentTimeMillis()

        log.info { "Starting $numBacktests backtests with parallelism=$parallelism for experiment $experimentId" }

        // Start persistence consumer in a separate coroutine (outside coroutineScope so it doesn't block)
        val persistenceJob =
            scope.launch {
                batchPersistenceService.consumeResults(experimentId, resultChannel, aggregator, numBacktests)
            }

        // Run all backtests - coroutineScope waits for all child coroutines
        coroutineScope {
            // Launch all backtest jobs
            (1..numBacktests).map { runNumber ->
                launch {
                    semaphore.withPermit {
                        try {
                            // Run backtest - candles are shared read-only
                            val result = BacktestEngine.runBacktest(config, candles)

                            // Send result to channel
                            resultChannel.send(BacktestResultWithRunNumber(result, runNumber))

                            // Log progress periodically
                            if (runNumber % properties.async.progressLogInterval == 0 || runNumber == numBacktests) {
                                val elapsed = System.currentTimeMillis() - backtestStartTime
                                val rate = runNumber * 1000.0 / elapsed
                                log.info {
                                    "Experiment $experimentId: $runNumber/$numBacktests complete (${String.format(
                                        "%.1f",
                                        rate,
                                    )} runs/sec)"
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log.warn(e) { "Backtest run $runNumber failed for experiment $experimentId" }
                            batchPersistenceService.incrementFailedRuns(experimentId)
                        }
                    }
                }
            }
        }

        // Step 5: Close channel and wait for persistence to complete
        resultChannel.close()
        persistenceJob.join()

        // Step 7: Finalize experiment with aggregated statistics
        val backtestTime = System.currentTimeMillis() - backtestStartTime
        log.info { "Completed $numBacktests backtests in ${backtestTime}ms for experiment $experimentId" }

        // Get actual dates from the candles
        val actualStartDate = candles.first().openTime
        val actualEndDate = candles.last().openTime

        batchPersistenceService.finalizeExperiment(experimentId, aggregator, actualStartDate, actualEndDate)

        log.info { "Experiment $experimentId completed successfully" }
    }

    private fun updateExperimentStatus(
        experimentId: Long,
        status: ExperimentStatus,
    ) {
        val experiment = experimentRepository.findById(experimentId).orElse(null)
        if (experiment != null) {
            experiment.status = status
            if (status == ExperimentStatus.RUNNING) {
                experiment.startedAt = Instant.now()
            }
            experimentRepository.save(experiment)
        }
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
     * Checks if an experiment is currently running.
     */
    fun isRunning(experimentId: Long): Boolean = activeJobs[experimentId]?.isActive == true

    /**
     * Graceful shutdown - cancel all running experiments and wait for completion.
     */
    @PreDestroy
    fun shutdown() {
        log.info { "Shutting down AsyncExperimentExecutor... (${activeJobs.size} active jobs)" }

        // Mark all running experiments as failed due to shutdown
        activeJobs.keys.forEach { experimentId ->
            batchPersistenceService.markExperimentFailed(experimentId, "Server shutdown")
        }

        // Cancel scope and wait for jobs to complete
        scope.cancel()

        runBlocking {
            withTimeoutOrNull(properties.async.shutdownTimeoutMs) {
                activeJobs.values.forEach { it.join() }
            } ?: log.warn { "Shutdown timeout - some jobs did not complete" }
        }

        log.info { "AsyncExperimentExecutor shutdown complete" }
    }
}
