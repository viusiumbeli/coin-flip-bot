package com.trading.coinflip.exchange.bybit

import com.fasterxml.jackson.databind.JsonNode
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
 * ByBit REST API client for historical kline data.
 * Implements ExchangeClient interface for exchange abstraction.
 *
 * ByBit API v5 characteristics:
 * - Endpoint: /v5/market/kline
 * - Category: linear (USDT perpetuals)
 * - Intervals: "1", "60", "240", "D" (minutes or D for day)
 * - Response: JSON with named fields (start, open, high, low, close, volume)
 * - Data order: Descending by default (newest first)
 */
class BybitClient(
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
        val interval = timeframe.toBybitInterval()

        val url =
            buildString {
                append("${config.baseUrl}/v5/market/kline")
                append("?category=linear")
                append("&symbol=$symbol")
                append("&interval=$interval")
                append("&limit=$limit")
                if (startTime != null) {
                    append("&start=${startTime.toEpochMilli()}")
                }
                if (endTime != null) {
                    append("&end=${endTime.toEpochMilli()}")
                }
            }

        return try {
            val response: HttpResponse = client.get(url)
            val body = response.bodyAsText()
            val root = objectMapper.readTree(body)

            // Check for API errors
            val retCode = root["retCode"]?.asInt() ?: -1
            if (retCode != 0) {
                val retMsg = root["retMsg"]?.asText() ?: "Unknown error"
                log.error { "ByBit API error: $retCode - $retMsg" }
                return emptyList()
            }

            val klines = root["result"]?.get("list") ?: return emptyList()

            // ByBit returns data in descending order (newest first), reverse for chronological
            klines
                .map { kline -> parseKline(kline, symbol, timeframe) }
                .reversed()
        } catch (e: CancellationException) {
            log.info { "Klines fetch cancelled for $symbol $timeframe startTime=$startTime" }
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch klines for $symbol $timeframe startTime=$startTime limit=$limit" }
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

            log.info { "Streaming historical data for $symbol $timeframe from $startDate (ByBit)" }

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

                // Rate limiting
                delay(config.rateLimitDelayMs)
            }

            log.info { "Completed streaming data for $symbol $timeframe (ByBit)" }
        }

    /**
     * Parse ByBit kline array to CandleEntity.
     * ByBit format: [startTime, open, high, low, close, volume, turnover]
     */
    private fun parseKline(
        kline: JsonNode,
        symbol: String,
        timeframe: Timeframe,
    ): CandleEntity {
        // ByBit returns array: [startTime, open, high, low, close, volume, turnover]
        return CandleEntity(
            symbol = symbol,
            timeframe = timeframe,
            openTime = Instant.ofEpochMilli(kline[0].asLong()),
            open = BigDecimal(kline[1].asText()),
            high = BigDecimal(kline[2].asText()),
            low = BigDecimal(kline[3].asText()),
            close = BigDecimal(kline[4].asText()),
            volume = BigDecimal(kline[5].asText()),
        )
    }

    companion object {
        /**
         * Convert Timeframe to ByBit interval string.
         * ByBit uses: "1", "60", "240", "D" (minutes or D for day)
         */
        fun Timeframe.toBybitInterval(): String =
            when (this) {
                Timeframe.ONE_MINUTE -> "1"
                Timeframe.ONE_HOUR -> "60"
                Timeframe.FOUR_HOURS -> "240"
                Timeframe.ONE_DAY -> "D"
            }
    }
}
