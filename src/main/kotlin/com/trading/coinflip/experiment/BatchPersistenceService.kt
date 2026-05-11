package com.trading.coinflip.experiment

import com.trading.coinflip.backtest.BacktestRunBulkRepository
import com.trading.coinflip.backtest.model.BacktestResultWithRunNumber
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.experiment.model.ExperimentStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for batch persisting backtest results to the database.
 * Consumes results from a channel and persists them in batches to optimize DB performance.
 */
@Service
class BatchPersistenceService(
    private val bulkInserter: BacktestRunBulkRepository,
    private val experimentRepository: ExperimentRepository,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    // Internal state for direct submission mode
    private val mutex = Mutex()
    private val batches = ConcurrentHashMap<Long, MutableList<BacktestResultWithRunNumber>>()
    private val batchCounts = ConcurrentHashMap<Long, Int>()

    /**
     * Submit a single result directly (no channel).
     * Batches internally and persists when threshold is reached.
     */
    suspend fun submitResult(
        experimentId: Long,
        result: BacktestResultWithRunNumber,
        aggregator: RunningAggregator,
    ) {
        val shouldPersist: List<BacktestResultWithRunNumber>?
        val batchNum: Int

        mutex.withLock {
            val batch = batches.getOrPut(experimentId) { mutableListOf() }
            batch.add(result)

            if (batch.size >= properties.async.batchSize) {
                shouldPersist = batch.toList()
                batch.clear()
                batchNum = batchCounts.merge(experimentId, 1, Int::plus) ?: 1
            } else {
                shouldPersist = null
                batchNum = 0
            }
        }

        if (shouldPersist != null) {
            val batchStartTime = System.currentTimeMillis()
            val aggStart = System.currentTimeMillis()
            aggregator.addAll(shouldPersist.map { it.result })
            log.info { "Aggregation: ${shouldPersist.size} items in ${System.currentTimeMillis() - aggStart}ms" }
            bulkInserter.persistBatch(experimentId, shouldPersist)
            log.info { "Batch $batchNum total time: ${System.currentTimeMillis() - batchStartTime}ms" }
            experimentRepository.updateProgress(experimentId, aggregator.getCount())
        }
    }

    /**
     * Flush any remaining results after all submissions are done.
     */
    suspend fun flushRemaining(
        experimentId: Long,
        aggregator: RunningAggregator,
    ) {
        val remaining =
            mutex.withLock {
                batches.remove(experimentId)?.toList()
            }
        batchCounts.remove(experimentId)

        if (!remaining.isNullOrEmpty()) {
            aggregator.addAll(remaining.map { it.result })
            bulkInserter.persistBatch(experimentId, remaining)
            experimentRepository.updateProgress(experimentId, aggregator.getCount())
        }

        log.info { "Finished processing results for experiment $experimentId. Total: ${aggregator.getCount()}" }
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
}
