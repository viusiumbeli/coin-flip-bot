package com.trading.coinflip.experiment

import com.trading.coinflip.backtest.BacktestRunRepository
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.dto.BacktestResultWithRunNumber
import com.trading.coinflip.common.model.BacktestRunEntity
import com.trading.coinflip.common.model.ExperimentTradeEntity
import com.trading.coinflip.data.ExperimentRepository
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for transactional persistence operations.
 * Extracted from BatchPersistenceService to avoid @Transactional self-invocation issues.
 */
@Service
class BatchPersistenceTransactionService(
    private val backtestRunRepository: BacktestRunRepository,
    private val experimentRepository: ExperimentRepository,
    private val experimentTradeRepository: ExperimentTradeRepository,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Persists a batch of backtest results in a single transaction.
     * Saves trades only for small experiments (numBacktests <= tradesThreshold).
     */
    @Transactional
    suspend fun persistBatch(
        experimentId: Long,
        batch: List<BacktestResultWithRunNumber>,
        numBacktests: Int,
    ) {
        val runs =
            batch.map { (result, runNumber) ->
                BacktestRunEntity(
                    experimentId = experimentId,
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

        val savedRuns = backtestRunRepository.saveAll(runs).toList()
        log.debug { "Persisted batch of ${runs.size} backtest runs for experiment $experimentId" }

        // Save trades only for small experiments
        if (numBacktests <= properties.experiment.tradesThreshold) {
            val allTrades = mutableListOf<ExperimentTradeEntity>()

            savedRuns.forEachIndexed { index, savedRun ->
                val result = batch[index].result
                val trades =
                    result.trades.mapIndexed { tradeIndex, trade ->
                        ExperimentTradeEntity(
                            backtestRunId = savedRun.id!!,
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
                experimentTradeRepository.saveAll(allTrades).toList()
                log.debug { "Persisted ${allTrades.size} trades for experiment $experimentId" }
            }
        }
    }

    /**
     * Updates the experiment's completed runs count.
     */
    @Transactional
    suspend fun updateProgress(
        experimentId: Long,
        completedRuns: Int,
    ) {
        val experiment = experimentRepository.findById(experimentId)
        if (experiment != null) {
            experiment.completedRuns = completedRuns
            experimentRepository.save(experiment)
        }
    }
}
