package com.trading.coinflip.backtest

import com.trading.coinflip.api.backtest.BacktestRequest
import com.trading.coinflip.backtest.model.BacktestConfig
import com.trading.coinflip.backtest.model.BacktestResult
import com.trading.coinflip.candle.CandleService
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.model.MutableTradingState
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class BacktestService(
    private val properties: BacktestProperties,
    private val candleService: CandleService,
    private val backtestEngine: BacktestEngine,
) {
    private val log = KotlinLogging.logger {}

    suspend fun runBacktestForSymbol(request: BacktestRequest): BacktestResult {
        log.info { "Processing: ${request.symbol} - ${request.timeframe.label}" }

        val config =
            BacktestConfig(
                symbol = request.symbol,
                timeframe = request.timeframe,
                initialCapital = properties.initialCapital,
                trading = properties.trading,
                startDate = request.startDate,
                endDate = request.endDate,
                collectTrades = request.timeframe != Timeframe.ONE_MINUTE,
            )

        val candles =
            candleService.loadCandlesParallel(
                symbol = request.symbol,
                timeframe = request.timeframe,
                startTime = request.startDate,
                endTime = request.endDate,
            )

        val state = MutableTradingState.create(config.initialCapital, config.collectTrades)
        val result = backtestEngine.runBacktest(state, config, candles)

        log.info { "Backtest complete" }
        return result
    }
}
