package com.trading.coinflip.exchange.deribit

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.ExchangeClient
import com.trading.coinflip.exchange.ExchangeRestConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.Instant

/**
 * Deribit REST API client for historical kline (TradingView chart) data.
 * Implements ExchangeClient interface for exchange abstraction.
 *
 * Deribit API characteristics:
 * - Endpoint: /public/get_tradingview_chart_data
 * - Parameters: instrument_name, start_timestamp (ms), end_timestamp (ms), resolution
 * - Response: JSON-RPC wrapper { jsonrpc, result: { ticks, open, high, low, close, volume, status } }
 * - Resolutions: "1", "60", "180", "1D" (minutes or D for day)
 * - Returns all candles in requested range (no pagination needed per request)
 */
class DeribitClient(
    private val objectMapper: ObjectMapper,
    private val config: ExchangeRestConfig,
) : ExchangeClient {
    private val log = KotlinLogging.logger {}

    private val client =
        HttpClient(CIO) {
            engine {
                requestTimeout = config.httpTimeoutMs
            }
        }

    override suspend fun fetchHistoricalKlines(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant?,
        endTime: Instant?,
        limit: Int,
    ): List<CandleEntity> {
        val resolution = timeframe.toDeribitResolution()

        val effectiveStart = startTime ?: Instant.now().minusSeconds(86400)
        val effectiveEnd = endTime ?: Instant.now()

        val url =
            buildString {
                append("${config.baseUrl}/public/get_tradingview_chart_data")
                append("?instrument_name=$symbol")
                append("&start_timestamp=${effectiveStart.toEpochMilli()}")
                append("&end_timestamp=${effectiveEnd.toEpochMilli()}")
                append("&resolution=$resolution")
            }

        return try {
            val response: HttpResponse = client.get(url)
            val body = response.bodyAsText()
            val root = objectMapper.readTree(body)

            val result =
                root["result"] ?: run {
                    val error = root["error"]
                    if (error != null) {
                        log.error { "Deribit API error: ${error["message"]?.asText()}" }
                    }
                    return emptyList()
                }

            val status = result["status"]?.asText()
            if (status != "ok") {
                log.debug { "Deribit chart data status: $status for $symbol" }
                return emptyList()
            }

            val ticks = result["ticks"] ?: return emptyList()
            val opens = result["open"] ?: return emptyList()
            val highs = result["high"] ?: return emptyList()
            val lows = result["low"] ?: return emptyList()
            val closes = result["close"] ?: return emptyList()
            val volumes = result["volume"] ?: return emptyList()

            val candles = mutableListOf<CandleEntity>()
            for (i in 0 until ticks.size()) {
                candles.add(
                    CandleEntity(
                        symbol = symbol,
                        timeframe = timeframe,
                        openTime = Instant.ofEpochMilli(ticks[i].asLong()),
                        open = BigDecimal(opens[i].asText()),
                        high = BigDecimal(highs[i].asText()),
                        low = BigDecimal(lows[i].asText()),
                        close = BigDecimal(closes[i].asText()),
                        volume = BigDecimal(volumes[i].asText()),
                    ),
                )
            }

            candles
        } catch (e: CancellationException) {
            log.info { "Klines fetch cancelled for $symbol $timeframe startTime=$startTime" }
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch klines for $symbol $timeframe startTime=$startTime" }
            emptyList()
        }
    }

    override fun streamHistoricalData(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
    ): Flow<List<CandleEntity>> =
        flow {
            var currentStartTime = startDate
            val now = Instant.now()
            val batchDurationMs = timeframe.minutes * 60 * 1000L * BATCH_SIZE
            var consecutiveEmpty = 0

            log.info { "Streaming historical data for $symbol $timeframe from $startDate (Deribit)" }

            while (currentStartTime.isBefore(now)) {
                val batchEnd =
                    Instant.ofEpochMilli(
                        (currentStartTime.toEpochMilli() + batchDurationMs).coerceAtMost(now.toEpochMilli()),
                    )

                val page =
                    fetchHistoricalKlines(
                        symbol = symbol,
                        timeframe = timeframe,
                        startTime = currentStartTime,
                        endTime = batchEnd,
                    )

                if (page.isEmpty()) {
                    consecutiveEmpty++
                    if (consecutiveEmpty >= MAX_CONSECUTIVE_EMPTY) {
                        log.warn {
                            "No data after $consecutiveEmpty consecutive empty batches for $symbol, stopping"
                        }
                        break
                    }
                    // Exponential skip: jump further forward on each consecutive empty
                    val skipMultiplier = 1L shl consecutiveEmpty.coerceAtMost(10)
                    val skipMs = batchDurationMs * skipMultiplier
                    currentStartTime =
                        Instant.ofEpochMilli(
                            (currentStartTime.toEpochMilli() + skipMs).coerceAtMost(now.toEpochMilli()),
                        )
                    delay(config.rateLimitDelayMs)
                    continue
                }

                consecutiveEmpty = 0

                // Filter incomplete candles (only matters for last page)
                val closedCandles =
                    page.filter { candle ->
                        val candleCloseTime = candle.openTime.plusMillis(timeframe.minutes * 60 * 1000L)
                        !candleCloseTime.isAfter(now)
                    }

                if (closedCandles.isNotEmpty()) {
                    emit(closedCandles)
                }

                // Move to next batch after last candle
                currentStartTime = page.last().openTime.plusMillis(timeframe.minutes * 60 * 1000L)

                // Rate limiting
                delay(config.rateLimitDelayMs)
            }

            log.info { "Completed streaming data for $symbol $timeframe (Deribit)" }
        }

    companion object {
        // Number of candles per batch request
        private const val BATCH_SIZE = 1000
        private const val MAX_CONSECUTIVE_EMPTY = 50

        /**
         * Convert Timeframe to Deribit resolution string.
         * Deribit uses: "1", "60", "180", "1D"
         * Note: Deribit doesn't support 240min; closest is 180min (3h).
         */
        fun Timeframe.toDeribitResolution(): String =
            when (this) {
                Timeframe.ONE_MINUTE -> "1"
                Timeframe.ONE_HOUR -> "60"
                Timeframe.FOUR_HOURS -> "180"
                Timeframe.ONE_DAY -> "1D"
            }
    }
}
