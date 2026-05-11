package com.trading.coinflip.backtest

import com.trading.coinflip.backtest.model.BacktestConfig
import com.trading.coinflip.backtest.model.BacktestResult
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.data.CandleRepository
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BacktestService(
    private val properties: BacktestProperties,
    private val candleRepository: CandleRepository,
    private val backtestEngine: BacktestEngine,
) {
    private val log = KotlinLogging.logger {}

    suspend fun runBacktestForSymbol(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant? = null,
        endDate: Instant? = null,
    ): BacktestResult {
        log.debug { "Processing: $symbol - ${timeframe.label}" }

        // Get candles for backtest
        val candles =
            candleRepository
                .findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                    symbol = symbol,
                    timeframe = timeframe,
                    startTime = startDate ?: properties.startDate,
                    endTime = endDate ?: Instant.now(),
                ).toList()

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
