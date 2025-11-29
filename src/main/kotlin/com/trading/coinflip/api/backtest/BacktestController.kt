package com.trading.coinflip.api.backtest

import com.trading.coinflip.api.data.AvailableSymbolsResponse
import com.trading.coinflip.backtest.BacktestService
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/backtest")
@CrossOrigin(origins = ["*"])
class BacktestController(
    private val backtestService: BacktestService,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    @PostMapping("/run")
    fun runBacktest(
        @RequestBody request: BacktestRequest,
    ): ResponseEntity<BacktestResponse> =
        try {
            log.info { "Received backtest request for ${request.symbol} ${request.timeframe.label}" }

            val result =
                backtestService.runBacktestForSymbol(
                    symbol = request.symbol,
                    timeframe = request.timeframe,
                    startDate = request.startDate,
                    endDate = request.endDate,
                )

            ResponseEntity.ok(result.toResponse())
        } catch (e: Exception) {
            log.error(e) { "Error running backtest: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }

    @PostMapping("/run-all")
    fun runAllBacktests(): ResponseEntity<List<BacktestResponse>> =
        try {
            log.info { "Received request to run all backtests" }

            val results = backtestService.runBacktest()

            ResponseEntity.ok(results.map { it.toResponse() })
        } catch (e: Exception) {
            log.error(e) { "Error running backtests: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }

    @GetMapping("/symbols")
    fun getAvailableSymbols(): ResponseEntity<AvailableSymbolsResponse> =
        ResponseEntity.ok(
            AvailableSymbolsResponse(
                symbols = properties.symbols,
                timeframes = Timeframe.entries.map { it.label },
            ),
        )

    @GetMapping("/config")
    fun getConfig(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "symbols" to properties.symbols,
                "timeframes" to properties.timeframes.map { it.label },
                "initialCapital" to properties.initialCapital,
                "riskPerTrade" to properties.trading.riskPerTrade,
                "atrPeriod" to properties.trading.atrPeriod,
                "atrMultiplier" to properties.trading.atrMultiplier,
                "transactionCostPercent" to properties.trading.transactionCostPercent,
                "maxConcurrentPositions" to properties.trading.maxConcurrentPositions,
            ),
        )
}
