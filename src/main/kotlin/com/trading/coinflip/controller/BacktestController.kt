package com.trading.coinflip.controller

import com.trading.coinflip.BacktestService
import com.trading.coinflip.config.BacktestProperties
import com.trading.coinflip.dto.AvailableSymbolsResponse
import com.trading.coinflip.dto.BacktestRequest
import com.trading.coinflip.dto.BacktestResponse
import com.trading.coinflip.dto.toDto
import com.trading.coinflip.model.Timeframe
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

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
    ): ResponseEntity<BacktestResponse> {
        return try {
            log.info { "Received backtest request for ${request.symbol} ${request.timeframe}" }

            val timeframe =
                Timeframe.fromLabel(request.timeframe)
                    ?: return ResponseEntity.badRequest().build()

            val startDate = request.startDate?.let { Instant.parse(it) }
            val endDate = request.endDate?.let { Instant.parse(it) }

            val result =
                backtestService.runBacktestForSymbol(
                    symbol = request.symbol,
                    timeframe = timeframe,
                    startDate = startDate,
                    endDate = endDate,
                )

            ResponseEntity.ok(result.toDto())
        } catch (e: Exception) {
            log.error(e) { "Error running backtest: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/run-all")
    fun runAllBacktests(): ResponseEntity<List<BacktestResponse>> =
        try {
            log.info { "Received request to run all backtests" }

            val results = backtestService.runBacktest()

            ResponseEntity.ok(results.map { it.toDto() })
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
