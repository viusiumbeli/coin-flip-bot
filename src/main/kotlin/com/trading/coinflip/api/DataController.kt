package com.trading.coinflip.api

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.data.CandleRepository
import com.trading.coinflip.data.DataService
import com.trading.coinflip.data.DataStatus
import com.trading.coinflip.data.SyncRequest
import com.trading.coinflip.data.SyncResult
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = ["*"])
class DataController(
    private val dataService: DataService,
    private val candleRepository: CandleRepository,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    @GetMapping("/status")
    fun getDataStatus(): ResponseEntity<List<DataStatus>> {
        val statusList = mutableListOf<DataStatus>()
        val now = Instant.now()

        for (symbol in properties.symbols) {
            for (timeframe in properties.timeframes) {
                val earliest = candleRepository.findEarliestCandleTime(symbol, timeframe)
                val latest = candleRepository.findLatestCandleTime(symbol, timeframe)
                val count =
                    if (earliest != null) {
                        candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe).size.toLong()
                    } else {
                        0L
                    }

                val hoursOutdated =
                    if (latest != null) {
                        val expectedNextCandle = latest.plusSeconds(timeframe.minutes * 60L)
                        val hoursBehind = Duration.between(expectedNextCandle, now).toHours()
                        if (hoursBehind > 0) hoursBehind else null
                    } else {
                        null
                    }

                statusList.add(
                    DataStatus(
                        symbol = symbol,
                        timeframe = timeframe.label,
                        candleCount = count,
                        earliestCandle = earliest,
                        latestCandle = latest,
                        hoursOutdated = hoursOutdated,
                    ),
                )
            }
        }

        return ResponseEntity.ok(statusList)
    }

    @PostMapping("/sync")
    fun syncData(
        @RequestBody request: SyncRequest,
    ): ResponseEntity<SyncResult> {
        return try {
            log.info { "Sync request for ${request.symbol} ${request.timeframe}" }

            val timeframe =
                Timeframe.fromLabel(request.timeframe)
                    ?: return ResponseEntity.badRequest().body(
                        SyncResult(
                            symbol = request.symbol,
                            timeframe = request.timeframe,
                            newCandlesAdded = 0,
                            success = false,
                            error = "Invalid timeframe: ${request.timeframe}",
                        ),
                    )

            val newCandlesAdded = dataService.syncMissingData(request.symbol, timeframe)

            ResponseEntity.ok(
                SyncResult(
                    symbol = request.symbol,
                    timeframe = request.timeframe,
                    newCandlesAdded = newCandlesAdded,
                    success = true,
                ),
            )
        } catch (e: Exception) {
            log.error(e) { "Error syncing data for ${request.symbol} ${request.timeframe}" }
            ResponseEntity.ok(
                SyncResult(
                    symbol = request.symbol,
                    timeframe = request.timeframe,
                    newCandlesAdded = 0,
                    success = false,
                    error = e.message ?: "Unknown error",
                ),
            )
        }
    }

    @PostMapping("/sync-all")
    fun syncAllData(): ResponseEntity<List<SyncResult>> {
        val results = mutableListOf<SyncResult>()

        for (symbol in properties.symbols) {
            for (timeframe in properties.timeframes) {
                try {
                    log.info { "Syncing $symbol ${timeframe.label}" }
                    val newCandlesAdded = dataService.syncMissingData(symbol, timeframe)
                    results.add(
                        SyncResult(
                            symbol = symbol,
                            timeframe = timeframe.label,
                            newCandlesAdded = newCandlesAdded,
                            success = true,
                        ),
                    )
                } catch (e: Exception) {
                    log.error(e) { "Error syncing $symbol ${timeframe.label}" }
                    results.add(
                        SyncResult(
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

        return ResponseEntity.ok(results)
    }
}
