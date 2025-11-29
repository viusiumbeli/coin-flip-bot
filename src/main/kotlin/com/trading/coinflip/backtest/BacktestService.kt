package com.trading.coinflip.backtest

import com.trading.coinflip.analytics.ReportGenerator
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.BacktestConfig
import com.trading.coinflip.common.model.BacktestResult
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.data.DataService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BacktestService(
    private val properties: BacktestProperties,
    private val dataService: DataService,
    private val reportGenerator: ReportGenerator,
    private val backtestEngine: BacktestEngine,
) {
    private val log = KotlinLogging.logger {}

    suspend fun runBacktest(): List<BacktestResult> {
        log.info { "=".repeat(80) }
        log.info { "Van Tharp Coin-Flip Trading Bot - Backtest System" }
        log.info { "=".repeat(80) }

        val allResults = mutableListOf<BacktestResult>()

        // Load and backtest each symbol and timeframe combination
        for (symbol in properties.symbols) {
            for (timeframe in properties.timeframes) {
                log.info { "\n" + "=".repeat(80) }
                log.info { "Processing: $symbol - ${timeframe.label}" }
                log.info { "=".repeat(80) }

                try {
                    // Load historical data
                    dataService.loadHistoricalData(
                        symbol = symbol,
                        timeframe = timeframe,
                        startDate = properties.startDate,
                    )

                    log.info { dataService.getDataSummary(symbol, timeframe) }

                    // Get candles for backtest
                    val candles =
                        dataService.getCandlesForBacktest(
                            symbol = symbol,
                            timeframe = timeframe,
                            startDate = properties.startDate,
                            endDate = Instant.now(),
                        )

                    if (candles.isEmpty()) {
                        log.warn { "No candles available for $symbol ${timeframe.label}" }
                        continue
                    }

                    // Create backtest config
                    val config =
                        BacktestConfig(
                            symbol = symbol,
                            timeframe = timeframe,
                            initialCapital = properties.initialCapital,
                            trading = properties.trading,
                            startDate = properties.startDate,
                            endDate = Instant.now(),
                        )

                    // Run backtest
                    val result = backtestEngine.runBacktest(config, candles)
                    allResults.add(result)

                    // Print individual result
                    reportGenerator.printResult(result)
                } catch (e: Exception) {
                    log.error(e) { "Error processing $symbol ${timeframe.label}" }
                }
            }
        }

        // Print comparison report
        if (allResults.isNotEmpty()) {
            log.info { "\n" + "=".repeat(80) }
            log.info { "FINAL COMPARISON REPORT" }
            log.info { "=".repeat(80) }
            reportGenerator.printComparisonReport(allResults)
            reportGenerator.exportResultsToCsv(allResults, "backtest_results.csv")
        }

        log.info { "\n" + "=".repeat(80) }
        log.info { "Backtest completed!" }
        log.info { "=".repeat(80) }

        return allResults
    }

    suspend fun runBacktestForSymbol(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant? = null,
        endDate: Instant? = null,
    ): BacktestResult {
        log.debug { "Processing: $symbol - ${timeframe.label}" }

        // Load historical data
        dataService.loadHistoricalData(
            symbol = symbol,
            timeframe = timeframe,
            startDate = startDate ?: properties.startDate,
        )

        log.debug { dataService.getDataSummary(symbol, timeframe) }

        // Get candles for backtest
        val candles =
            dataService.getCandlesForBacktest(
                symbol = symbol,
                timeframe = timeframe,
                startDate = startDate ?: properties.startDate,
                endDate = endDate ?: Instant.now(),
            )

        require(candles.isNotEmpty()) { "No candles available for $symbol ${timeframe.label}" }

        // Create backtest config
        val config =
            BacktestConfig(
                symbol = symbol,
                timeframe = timeframe,
                initialCapital = properties.initialCapital,
                trading = properties.trading,
                startDate = startDate ?: properties.startDate,
                endDate = endDate ?: Instant.now(),
            )

        // Run backtest
        return backtestEngine.runBacktest(config, candles)
    }
}
