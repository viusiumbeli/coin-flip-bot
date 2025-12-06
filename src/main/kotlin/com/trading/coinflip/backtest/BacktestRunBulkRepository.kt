package com.trading.coinflip.backtest

import com.trading.coinflip.backtest.model.BacktestResultWithRunNumber
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service

/**
 * Bulk inserts backtest runs using native multi-row INSERT for performance.
 * Much faster than saveAll() which inserts one row at a time.
 */
@Service
class BacktestRunBulkRepository(
    private val databaseClient: DatabaseClient,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Persists a batch of backtest results using native multi-row INSERT.
     * Single statement, auto-commits. Trades are never saved for experiments.
     */
    suspend fun persistBatch(
        experimentId: Long,
        batch: List<BacktestResultWithRunNumber>,
    ) {
        if (batch.isEmpty()) return

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
    }

    /**
     * Builds a multi-row INSERT statement for backtest_runs.
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
}
