package com.trading.coinflip.api.candle

import com.trading.coinflip.candle.CandleService
import com.trading.coinflip.common.config.BacktestProperties
import mu.KotlinLogging
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api/candles")
@CrossOrigin(origins = ["*"])
class CandleController(
    private val candleService: CandleService,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    @GetMapping("/status")
    suspend fun getDataStatus(): List<CandleStatusResponse> {
        val now = Instant.now()

        // Single aggregation query for all symbol/timeframe combinations
        val statsMap =
            candleService
                .getAllCandleStats()
                .associateBy { "${it.symbol}:${it.timeframe}" }

        return properties.symbols.flatMap { symbol ->
            properties.timeframes.map { timeframe ->
                val stats = statsMap["$symbol:${timeframe.name}"]
                val latest = stats?.latest

                val hoursOutdated =
                    if (latest != null) {
                        val expectedNextCandle = latest.plusSeconds(timeframe.minutes * 60L)
                        val hoursBehind = Duration.between(expectedNextCandle, now).toHours()
                        if (hoursBehind > 0) hoursBehind else null
                    } else {
                        null
                    }

                CandleStatusResponse(
                    symbol = symbol,
                    timeframe = timeframe.label,
                    candleCount = stats?.candleCount ?: 0L,
                    earliestCandle = stats?.earliest,
                    latestCandle = latest,
                    hoursOutdated = hoursOutdated,
                )
            }
        }
    }

    @PostMapping("/sync")
    suspend fun syncData(
        @RequestBody request: SyncRequest,
    ): SyncResponse =
        try {
            log.info { "Sync request for ${request.symbol} ${request.timeframe.label}" }

            val newCandlesAdded = candleService.syncMissingData(request.symbol, request.timeframe)

            SyncResponse(
                symbol = request.symbol,
                timeframe = request.timeframe.label,
                newCandlesAdded = newCandlesAdded,
                success = true,
            )
        } catch (e: Exception) {
            log.error(e) { "Error syncing data for ${request.symbol} ${request.timeframe.label}" }
            SyncResponse(
                symbol = request.symbol,
                timeframe = request.timeframe.label,
                newCandlesAdded = 0,
                success = false,
                error = e.message ?: "Unknown error",
            )
        }

    @PostMapping("/sync-all")
    suspend fun syncAllData(): List<SyncResponse> {
        val results = mutableListOf<SyncResponse>()

        for (symbol in properties.symbols) {
            for (timeframe in properties.timeframes) {
                try {
                    log.info { "Syncing $symbol ${timeframe.label}" }
                    val newCandlesAdded = candleService.syncMissingData(symbol, timeframe)
                    results.add(
                        SyncResponse(
                            symbol = symbol,
                            timeframe = timeframe.label,
                            newCandlesAdded = newCandlesAdded,
                            success = true,
                        ),
                    )
                } catch (e: Exception) {
                    log.error(e) { "Error syncing $symbol ${timeframe.label}" }
                    results.add(
                        SyncResponse(
                            symbol = symbol,
                            timeframe = timeframe.label,
                            newCandlesAdded = 0,
                            success = false,
                            error = e.message ?: "Unknown error",
                        ),
                    )
                }
            }
        }

        return results
    }
}
