package com.trading.coinflip.experiment

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.BacktestResult
import com.trading.coinflip.common.model.BacktestRun
import com.trading.coinflip.common.model.ExperimentStatus
import com.trading.coinflip.common.model.ExperimentTrade
import com.trading.coinflip.data.BacktestRunRepository
import com.trading.coinflip.data.ExperimentRepository
import com.trading.coinflip.data.ExperimentTradeRepository
import kotlinx.coroutines.channels.ReceiveChannel
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Data class for backtest result with run number.
 */
data class BacktestResultWithRunNumber(
    val result: BacktestResult,
    val runNumber: Int,
)

/**
 * Service for batch persisting backtest results to the database.
 * Consumes results from a channel and persists them in batches to optimize DB performance.
 */
@Service
class BatchPersistenceService(
    private val backtestRunRepository: BacktestRunRepository,
    private val experimentRepository: ExperimentRepository,
    private val experimentTradeRepository: ExperimentTradeRepository,
    private val transactionTemplate: TransactionTemplate,
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
                persistBatch(experimentId, batch, numBacktests)
                updateProgress(experimentId, aggregator.getCount())
                batch.clear()
            }
        }

        // Persist any remaining results
        if (batch.isNotEmpty()) {
            persistBatch(experimentId, batch, numBacktests)
            updateProgress(experimentId, aggregator.getCount())
        }

        log.info { "Finished consuming results for experiment $experimentId. Total: ${aggregator.getCount()}" }
    }

    /**
     * Persists a batch of backtest results in a single transaction.
     * Saves trades only for small experiments (numBacktests <= 100).
     */
    private fun persistBatch(
        experimentId: Long,
        batch: List<BacktestResultWithRunNumber>,
        numBacktests: Int,
    ) {
        transactionTemplate.execute {
            val experiment = experimentRepository.getReferenceById(experimentId)

            val runs =
                batch.map { (result, runNumber) ->
                    BacktestRun(
                        experiment = experiment,
                        runNumber = runNumber,
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
                }

            val savedRuns = backtestRunRepository.saveAll(runs)
            log.debug { "Persisted batch of ${runs.size} backtest runs for experiment $experimentId" }

            // Save trades only for small experiments
            if (numBacktests <= properties.experiment.tradesThreshold) {
                val allTrades = mutableListOf<ExperimentTrade>()

                savedRuns.forEachIndexed { index, savedRun ->
                    val result = batch[index].result
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
                    allTrades.addAll(trades)
                }

                if (allTrades.isNotEmpty()) {
                    experimentTradeRepository.saveAll(allTrades)
                    log.debug { "Persisted ${allTrades.size} trades for experiment $experimentId" }
                }
            }
        }
    }

    /**
     * Updates the experiment's completed runs count.
     */
    private fun updateProgress(
        experimentId: Long,
        completedRuns: Int,
    ) {
        transactionTemplate.execute {
            val experiment = experimentRepository.findById(experimentId).orElse(null)
            if (experiment != null) {
                experiment.completedRuns = completedRuns
                experimentRepository.save(experiment)
            }
        }
    }

    /**
     * Finalizes the experiment with aggregated statistics.
     */
    fun finalizeExperiment(
        experimentId: Long,
        aggregator: RunningAggregator,
        startDate: Instant,
        endDate: Instant,
    ) {
        transactionTemplate.execute {
            val experiment =
                experimentRepository
                    .findById(experimentId)
                    .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

            val stats = aggregator.computeAverages()

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
                completedRuns = aggregator.getCount()
                finishedAt = Instant.now()
            }

            experimentRepository.save(experiment)
            log.info { "Finalized experiment $experimentId with ${aggregator.getCount()} runs" }
        }
    }

    /**
     * Marks an experiment as failed.
     */
    fun markExperimentFailed(
        experimentId: Long,
        errorMessage: String,
    ) {
        transactionTemplate.execute {
            val experiment = experimentRepository.findById(experimentId).orElse(null)
            if (experiment != null) {
                experiment.status = ExperimentStatus.FAILED
                experiment.errorMessage = errorMessage
                experiment.finishedAt = Instant.now()
                experimentRepository.save(experiment)
                log.error { "Marked experiment $experimentId as failed: $errorMessage" }
            }
        }
    }

    /**
     * Marks an experiment as cancelled.
     */
    fun markExperimentCancelled(experimentId: Long) {
        transactionTemplate.execute {
            val experiment = experimentRepository.findById(experimentId).orElse(null)
            if (experiment != null) {
                experiment.status = ExperimentStatus.CANCELLED
                experiment.finishedAt = Instant.now()
                experimentRepository.save(experiment)
                log.info { "Marked experiment $experimentId as cancelled" }
            }
        }
    }

    /**
     * Increments the failed runs count for an experiment.
     */
    fun incrementFailedRuns(experimentId: Long) {
        transactionTemplate.execute {
            val experiment = experimentRepository.findById(experimentId).orElse(null)
            if (experiment != null) {
                experiment.failedRuns++
                experimentRepository.save(experiment)
            }
        }
    }
}
