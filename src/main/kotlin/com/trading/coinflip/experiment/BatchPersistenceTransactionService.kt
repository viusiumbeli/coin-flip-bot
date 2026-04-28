package com.trading.coinflip.experiment

import com.trading.coinflip.backtest.BacktestRunRepository
import com.trading.coinflip.backtest.model.BacktestResultWithRunNumber
import com.trading.coinflip.backtest.model.BacktestRunEntity
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.experiment.model.ExperimentTradeEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for transactional persistence operations.
 * Extracted from BatchPersistenceService to avoid @Transactional self-invocation issues.
 */
@Service
class BatchPersistenceTransactionService(
    private val databaseClient: DatabaseClient,
    private val backtestRunRepository: BacktestRunRepository,
    private val experimentRepository: ExperimentRepository,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Persists a batch of backtest results.
     * Uses native multi-row INSERT for large experiments (single statement, auto-commits).
     * Saves trades only for small experiments (numBacktests <= tradesThreshold).
     * Note: @Transactional removed - single INSERT auto-commits, and using it with
     * Dispatchers.IO causes deadlock due to transaction commit dispatcher conflict.
     */
    suspend fun persistBatch(
        experimentId: Long,
        batch: List<BacktestResultWithRunNumber>,
        numBacktests: Int,
    ) {
        if (batch.isEmpty()) return

        // For large experiments, use fast multi-row INSERT (no trades saved)
        if (numBacktests > properties.experiment.tradesThreshold) {
            val startTime = System.currentTimeMillis()
            val sql = buildBacktestRunsInsert(experimentId, batch)

            try {
                databaseClient
                    .sql(sql)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()

                val duration = System.currentTimeMillis() - startTime
                log.info { "Multi-row INSERT: ${batch.size} rows in ${duration}ms for experiment $experimentId" }
            } catch (e: Exception) {
                log.error(e) { "Multi-row INSERT FAILED for experiment $experimentId! SQL length: ${sql.length}" }
                throw e
            }
            return
        }

        // For small experiments, use saveAll to get IDs for trades
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

        // Save trades for small experiments
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
            val tradeSql = buildTradesInsert(allTrades)
            databaseClient
                .sql(tradeSql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            log.debug { "Persisted ${allTrades.size} trades for experiment $experimentId" }
        }
    }

    /**
     * Builds a multi-row INSERT statement for backtest_runs.
     * Much faster than saveAll() which inserts one row at a time.
     * Uses toPlainString() for BigDecimals to avoid scientific notation in SQL.
     */
    private fun buildBacktestRunsInsert(
        experimentId: Long,
        batch: List<BacktestResultWithRunNumber>,
    ): String {
        val values =
            batch.joinToString(", ") { (result, runNumber) ->
                // Use toPlainString() to avoid scientific notation (e.g., 1E-8) in SQL
                """
                (
                    $experimentId, $runNumber,
                    ${result.finalCapital.toPlainString()}, ${result.totalReturn.toPlainString()},
                    ${result.totalReturnPercent.toPlainString()}, ${result.maxDrawdown.toPlainString()},
                    ${result.maxDrawdownPercent.toPlainString()}, ${result.winRate.toPlainString()},
                    ${result.profitFactor.toPlainString()}, ${result.sharpeRatio.toPlainString()},
                    ${result.totalTrades}, ${result.winningTrades}, ${result.losingTrades},
                    ${result.averageWin.toPlainString()}, ${result.averageLoss.toPlainString()},
                    ${result.largestWin.toPlainString()}, ${result.largestLoss.toPlainString()},
                    ${result.averageTradeDuration},
                    ${result.buyAndHoldReturn.toPlainString()}, ${result.buyAndHoldReturnPercent.toPlainString()}
                )
                """.trimIndent().replace("\n", " ")
            }

        return """
            INSERT INTO backtest_runs (
                experiment_id, run_number, final_capital, total_return,
                total_return_percent, max_drawdown, max_drawdown_percent,
                win_rate, profit_factor, sharpe_ratio,
                total_trades, winning_trades, losing_trades,
                average_win, average_loss, largest_win,
                largest_loss, average_trade_duration,
                buy_and_hold_return, buy_and_hold_return_percent
            ) VALUES $values
            """.trimIndent()
    }

    /**
     * Builds a multi-row INSERT statement for experiment_trades.
     * Much faster than saveAll() which inserts one row at a time.
     */
    private fun buildTradesInsert(trades: List<ExperimentTradeEntity>): String {
        val values =
            trades.joinToString(", ") { trade ->
                """
                (
                    ${trade.backtestRunId}, ${trade.tradeNumber},
                    '${trade.symbol}', '${trade.timeframe.name}', '${trade.side.name}',
                    '${trade.entryTime}', ${trade.entryPrice.toPlainString()},
                    '${trade.exitTime}', ${trade.exitPrice.toPlainString()},
                    ${trade.positionSize.toPlainString()}, ${trade.initialStopLoss.toPlainString()},
                    ${trade.trailingStop.toPlainString()}, ${trade.profitLoss.toPlainString()},
                    ${trade.profitLossPercent.toPlainString()},
                    '${trade.exitReason.replace("'", "''")}',
                    ${trade.balanceBeforeOpen.toPlainString()}, ${trade.balanceAfterOpen.toPlainString()},
                    ${trade.balanceBeforeClose.toPlainString()}, ${trade.balanceAfterClose.toPlainString()}
                )
                """.trimIndent().replace("\n", " ")
            }

        return """
            INSERT INTO experiment_trades (
                backtest_run_id, trade_number, symbol, timeframe, side,
                entry_time, entry_price, exit_time, exit_price,
                position_size, initial_stop_loss, trailing_stop,
                profit_loss, profit_loss_percent, exit_reason,
                balance_before_open, balance_after_open,
                balance_before_close, balance_after_close
            ) VALUES $values
            """.trimIndent()
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
