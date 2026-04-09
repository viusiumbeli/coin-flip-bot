package com.trading.coinflip

import com.trading.coinflip.analytics.ReportGenerator
import com.trading.coinflip.backtesting.BacktestEngine
import com.trading.coinflip.config.BacktestProperties
import com.trading.coinflip.data.DataService
import com.trading.coinflip.model.BacktestConfig
import com.trading.coinflip.model.BacktestResult
import com.trading.coinflip.model.Timeframe
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BacktestService(
    private val properties: BacktestProperties,
    private val dataService: DataService,
    private val backtestEngine: BacktestEngine,
    private val reportGenerator: ReportGenerator,
) {
    private val log = KotlinLogging.logger {}

    fun runBacktest(): List<BacktestResult> {
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
                        startDate = properties.startDate ?: java.time.Instant.parse("2017-01-01T00:00:00Z"),
                    )

                    log.info { dataService.getDataSummary(symbol, timeframe) }

                    // Get candles for backtest
                    val candles =
                        dataService.getCandlesForBacktest(
                            symbol = symbol,
                            timeframe = timeframe,
                            startDate = properties.startDate,
                            endDate = properties.endDate,
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
                            riskPerTrade = properties.riskPerTrade,
                            atrPeriod = properties.atrPeriod,
                            atrMultiplier = properties.atrMultiplier,
                            transactionCostPercent = properties.transactionCostPercent,
                            maxConcurrentPositions = properties.maxConcurrentPositions,
                            startDate = properties.startDate,
                            endDate = properties.endDate,
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

    fun runBacktestForSymbol(
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
            startDate = startDate ?: properties.startDate ?: Instant.parse("2017-01-01T00:00:00Z"),
        )

        log.debug { dataService.getDataSummary(symbol, timeframe) }

        // Get candles for backtest
        val candles =
            dataService.getCandlesForBacktest(
                symbol = symbol,
                timeframe = timeframe,
                startDate = startDate ?: properties.startDate,
                endDate = endDate ?: properties.endDate,
            )

        require(candles.isNotEmpty()) { "No candles available for $symbol ${timeframe.label}" }

        // Create backtest config
        val config =
            BacktestConfig(
                symbol = symbol,
                timeframe = timeframe,
                initialCapital = properties.initialCapital,
                riskPerTrade = properties.riskPerTrade,
                atrPeriod = properties.atrPeriod,
                atrMultiplier = properties.atrMultiplier,
                transactionCostPercent = properties.transactionCostPercent,
                maxConcurrentPositions = properties.maxConcurrentPositions,
                startDate = startDate ?: properties.startDate,
                endDate = endDate ?: properties.endDate,
            )

        // Run backtest
        return backtestEngine.runBacktest(config, candles)
    }
}
