package com.trading.coinflip.experiment

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.dto.BacktestResultWithRunNumber
import com.trading.coinflip.common.model.ExperimentStatus
import com.trading.coinflip.data.ExperimentRepository
import kotlinx.coroutines.channels.ReceiveChannel
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Service for batch persisting backtest results to the database.
 * Consumes results from a channel and persists them in batches to optimize DB performance.
 */
@Service
class BatchPersistenceService(
    private val transactionService: BatchPersistenceTransactionService,
    private val experimentRepository: ExperimentRepository,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Consumes backtest results from the channel and persists them in batches.
     * Updates experiment progress after each batch.
     *
     * @param experimentId The experiment ID to associate results with
     * @param channel The channel to receive results from
     * @param aggregator The running aggregator to track statistics
     * @param numBacktests The total number of backtests (trades saved only if <= 100)
     */
    suspend fun consumeResults(
        experimentId: Long,
        channel: ReceiveChannel<BacktestResultWithRunNumber>,
        aggregator: RunningAggregator,
        numBacktests: Int,
    ) {
        val batch = mutableListOf<BacktestResultWithRunNumber>()

        for (resultWithNumber in channel) {
            batch.add(resultWithNumber)
            aggregator.add(resultWithNumber.result)

            if (batch.size >= properties.async.batchSize) {
                transactionService.persistBatch(experimentId, batch, numBacktests)
                transactionService.updateProgress(experimentId, aggregator.getCount())
                batch.clear()
            }
        }

        // Persist any remaining results
        if (batch.isNotEmpty()) {
            transactionService.persistBatch(experimentId, batch, numBacktests)
            transactionService.updateProgress(experimentId, aggregator.getCount())
        }

        log.info { "Finished consuming results for experiment $experimentId. Total: ${aggregator.getCount()}" }
    }

    /**
     * Finalizes the experiment with aggregated statistics.
     */
    @Transactional
    suspend fun finalizeExperiment(
        experimentId: Long,
        aggregator: RunningAggregator,
        startDate: Instant,
        endDate: Instant,
    ) {
        val stats = aggregator.computeAverages()

        val experiment =
            experimentRepository.findById(experimentId)
                ?: throw IllegalArgumentException("Experiment not found: $experimentId")

        experiment.apply {
            finalCapital = stats.finalCapital
            totalReturn = stats.totalReturn
            totalReturnPercent = stats.totalReturnPercent
            maxDrawdown = stats.maxDrawdown
            maxDrawdownPercent = stats.maxDrawdownPercent
            winRate = stats.winRate
            profitFactor = stats.profitFactor
            sharpeRatio = stats.sharpeRatio
            totalTrades = stats.totalTrades
            winningTrades = stats.winningTrades
            losingTrades = stats.losingTrades
            averageWin = stats.averageWin
            averageLoss = stats.averageLoss
            largestWin = stats.largestWin
            largestLoss = stats.largestLoss
            averageTradeDuration = stats.averageTradeDuration
            buyAndHoldReturn = stats.buyAndHoldReturn
            buyAndHoldReturnPercent = stats.buyAndHoldReturnPercent
            runsBeatBuyHold = stats.runsBeatBuyHold

            // Variance metrics
            returnStdDev = stats.returnStdDev
            returnMin = stats.returnMin
            returnMax = stats.returnMax
            returnP5 = stats.returnP5
            returnP25 = stats.returnP25
            returnP50 = stats.returnP50
            returnP75 = stats.returnP75
            returnP95 = stats.returnP95

            this.startDate = startDate
            this.endDate = endDate

            status = ExperimentStatus.COMPLETED
            this.completedRuns = aggregator.getCount()
            finishedAt = Instant.now()
        }

        experimentRepository.save(experiment)
        log.info { "Finalized experiment $experimentId with ${aggregator.getCount()} runs" }
    }

    /**
     * Marks an experiment as failed.
     */
    @Transactional
    suspend fun markExperimentFailed(
        experimentId: Long,
        errorMessage: String,
    ) {
        val experiment = experimentRepository.findById(experimentId)
        if (experiment != null) {
            experiment.status = ExperimentStatus.FAILED
            experiment.errorMessage = errorMessage
            experiment.finishedAt = Instant.now()
            experimentRepository.save(experiment)
            log.error { "Marked experiment $experimentId as failed: $errorMessage" }
        }
    }

    /**
     * Marks an experiment as cancelled.
     */
    @Transactional
    suspend fun markExperimentCancelled(experimentId: Long) {
        val experiment = experimentRepository.findById(experimentId)
        if (experiment != null) {
            experiment.status = ExperimentStatus.CANCELLED
            experiment.finishedAt = Instant.now()
            experimentRepository.save(experiment)
            log.info { "Marked experiment $experimentId as cancelled" }
        }
    }

    /**
     * Increments the failed runs count for an experiment.
     */
    @Transactional
    suspend fun incrementFailedRuns(experimentId: Long) {
        val experiment = experimentRepository.findById(experimentId)
        if (experiment != null) {
            experiment.failedRuns++
            experimentRepository.save(experiment)
        }
    }
}
