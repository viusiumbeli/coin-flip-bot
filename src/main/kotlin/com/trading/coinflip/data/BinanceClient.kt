package com.trading.coinflip.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class BinanceClient(
    private val objectMapper: ObjectMapper,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    private val client =
        HttpClient(CIO) {
            engine {
                requestTimeout = properties.api.httpTimeoutMs
            }
        }

    private val baseUrl = "https://api.binance.com"

    suspend fun fetchHistoricalKlines(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant? = null,
        endTime: Instant? = null,
        limit: Int = 1000,
    ): List<CandleEntity> {
        val interval =
            when (timeframe) {
                Timeframe.ONE_MINUTE -> "1m"
                Timeframe.ONE_HOUR -> "1h"
                Timeframe.FOUR_HOURS -> "4h"
                Timeframe.ONE_DAY -> "1d"
            }

        val url =
            buildString {
                append("$baseUrl/api/v3/klines")
                append("?symbol=$symbol")
                append("&interval=$interval")
                append("&limit=$limit")
                if (startTime != null) {
                    append("&startTime=${startTime.toEpochMilli()}")
                }
                if (endTime != null) {
                    append("&endTime=${endTime.toEpochMilli()}")
                }
            }

        return try {
            val response: HttpResponse = client.get(url)
            val body = response.bodyAsText()
            val klines = objectMapper.readValue(body, Array<Array<Any>>::class.java)

            klines.map { kline ->
                CandleEntity(
                    symbol = symbol,
                    timeframe = timeframe,
                    openTime = Instant.ofEpochMilli((kline[0] as Number).toLong()),
                    open = BigDecimal(kline[1].toString()),
                    high = BigDecimal(kline[2].toString()),
                    low = BigDecimal(kline[3].toString()),
                    close = BigDecimal(kline[4].toString()),
                    volume = BigDecimal(kline[5].toString()),
                )
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch klines for $symbol $timeframe" }
            emptyList()
        }
    }

    fun streamHistoricalData(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
    ): Flow<List<CandleEntity>> =
        flow {
            var currentStartTime = startDate
            val now = Instant.now()

            log.info { "Streaming historical data for $symbol $timeframe from $startDate" }

            while (currentStartTime.isBefore(now)) {
                val page =
                    fetchHistoricalKlines(
                        symbol = symbol,
                        timeframe = timeframe,
                        startTime = currentStartTime,
                        limit = 1000,
                    )

                if (page.isEmpty()) {
                    break
                }

                // Filter incomplete candles (only matters for last page)
                val closedCandles =
                    page.filter { candle ->
                        val candleCloseTime = candle.openTime.plusMillis(timeframe.minutes * 60 * 1000L)
                        !candleCloseTime.isAfter(now)
                    }

                if (closedCandles.isNotEmpty()) {
                    emit(closedCandles)
                }

                // Move to next batch
                currentStartTime = page.last().openTime.plusMillis(timeframe.minutes * 60 * 1000L)

//                 Rate limiting
                delay(properties.api.rateLimitDelayMs)
            }

            log.info { "Completed streaming data for $symbol $timeframe" }
        }
}
