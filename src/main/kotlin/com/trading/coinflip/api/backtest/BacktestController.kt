package com.trading.coinflip.api.backtest

import com.trading.coinflip.api.data.AvailableSymbolsResponse
import com.trading.coinflip.backtest.BacktestService
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import mu.KotlinLogging
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/backtest")
@CrossOrigin(origins = ["*"])
class BacktestController(
    private val backtestService: BacktestService,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    @PostMapping("/run")
    suspend fun runBacktest(
        @RequestBody request: BacktestRequest,
    ): BacktestResponse {
        log.info { "Received backtest request for ${request.symbol} ${request.timeframe.label}" }

        val result =
            backtestService.runBacktestForSymbol(
                symbol = request.symbol,
                timeframe = request.timeframe,
                startDate = request.startDate,
                endDate = request.endDate,
            )

        return result.toResponse()
    }

    @PostMapping("/run-all")
    suspend fun runAllBacktests(): List<BacktestResponse> {
        log.info { "Received request to run all backtests" }

        val results = backtestService.runBacktest()

        return results.map { it.toResponse() }
    }

    @GetMapping("/symbols")
    fun getAvailableSymbols(): AvailableSymbolsResponse =
        AvailableSymbolsResponse(
            symbols = properties.symbols,
            timeframes = Timeframe.entries.map { it.label },
        )

    @GetMapping("/config")
    fun getConfig(): Map<String, Any> =
        mapOf(
            "symbols" to properties.symbols,
            "timeframes" to properties.timeframes.map { it.label },
            "initialCapital" to properties.initialCapital,
            "riskPerTrade" to properties.trading.riskPerTrade,
            "atrPeriod" to properties.trading.atrPeriod,
            "atrMultiplier" to properties.trading.atrMultiplier,
            "transactionCostPercent" to properties.trading.transactionCostPercent,
            "maxConcurrentPositions" to properties.trading.maxConcurrentPositions,
        )
}
