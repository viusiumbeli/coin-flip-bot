package com.trading.coinflip.exchange.binance

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.ExchangeWebSocketClient
import com.trading.coinflip.exchange.ExchangeWebSocketConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Binance WebSocket client for real-time kline streaming.
 * Implements ExchangeWebSocketClient interface for exchange abstraction.
 *
 * Binance WebSocket characteristics:
 * - URL format: wss://stream.binance.com:9443/ws/{symbol}@kline_{interval}
 * - Closed candle indicator: k.x = true
 * - Built-in ping/pong handling
 */
class BinanceWebSocketClient(
    private val objectMapper: ObjectMapper,
    private val config: ExchangeWebSocketConfig,
) : ExchangeWebSocketClient {
    private val log = KotlinLogging.logger {}

    private val client =
        HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = config.heartbeatIntervalMs
            }
        }

    private val running = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)

    override fun connectAndStream(
        symbol: String,
        timeframe: Timeframe,
        scope: CoroutineScope,
    ): Flow<CandleEntity> =
        channelFlow {
            val streamName = "${symbol.lowercase()}@kline_${timeframe.label}"
            val url = "${config.websocketUrl}/$streamName"

            running.set(true)

            while (running.get() && isActive) {
                try {
                    log.info { "Connecting to Binance WebSocket: $url" }

                    client.webSocket(url) {
                        reconnectAttempts.set(0)
                        log.info { "WebSocket connected for $symbol ${timeframe.label}" }

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    val candle = parseKlineMessage(text, symbol, timeframe)
                                    if (candle != null) {
                                        send(candle)
                                    }
                                }
                                is Frame.Close -> {
                                    log.warn { "WebSocket closed" }
                                    break
                                }
                                else -> { /* ignore ping/pong, binary */ }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    log.info { "WebSocket connection cancelled" }
                    throw e
                } catch (e: Exception) {
                    log.error(e) { "WebSocket error for $symbol" }

                    if (!running.get()) break

                    val attempts = reconnectAttempts.incrementAndGet()
                    if (attempts > config.maxReconnectAttempts) {
                        log.error { "Max reconnect attempts ($attempts) reached, stopping" }
                        break
                    }

                    // Exponential backoff: 5s, 10s, 20s, 40s...
                    val delayMs = config.reconnectDelayMs * (1L shl (attempts - 1).coerceAtMost(6))
                    log.info { "Reconnecting in ${delayMs}ms (attempt $attempts)" }
                    delay(delayMs)
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Parse Binance kline WebSocket message.
     * Returns CandleEntity only when candle is closed (k.x = true).
     */
    private fun parseKlineMessage(
        json: String,
        symbol: String,
        timeframe: Timeframe,
    ): CandleEntity? {
        return try {
            val node = objectMapper.readTree(json)
            val kline = node["k"] ?: return null

            // Only emit completed candles (x = true means candle is closed)
            val isClosed = kline["x"]?.asBoolean() ?: false
            if (!isClosed) return null

            CandleEntity(
                symbol = symbol,
                timeframe = timeframe,
                openTime = Instant.ofEpochMilli(kline["t"].asLong()),
                open = BigDecimal(kline["o"].asText()),
                high = BigDecimal(kline["h"].asText()),
                low = BigDecimal(kline["l"].asText()),
                close = BigDecimal(kline["c"].asText()),
                volume = BigDecimal(kline["v"].asText()),
            )
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse kline message: $json" }
            null
        }
    }

    override fun stop() {
        running.set(false)
    }

    override fun isRunning(): Boolean = running.get()

    override fun getReconnectAttempts(): Int = reconnectAttempts.get()
}
