package com.trading.coinflip.exchange.bybit

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.ExchangeWebSocketClient
import com.trading.coinflip.exchange.ExchangeWebSocketConfig
import com.trading.coinflip.exchange.bybit.BybitClient.Companion.toBybitInterval
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * ByBit WebSocket client for real-time kline streaming.
 * Implements ExchangeWebSocketClient interface for exchange abstraction.
 *
 * ByBit WebSocket v5 characteristics:
 * - URL: wss://stream.bybit.com/v5/public/linear (for USDT perpetuals)
 * - Subscribe via JSON message: {"op": "subscribe", "args": ["kline.{interval}.{symbol}"]}
 * - Closed candle indicator: confirm = true
 * - Manual heartbeat required: Send {"op": "ping"} every 20 seconds
 */
class BybitWebSocketClient(
    private val objectMapper: ObjectMapper,
    private val config: ExchangeWebSocketConfig,
) : ExchangeWebSocketClient {
    private val log = KotlinLogging.logger {}

    private val client =
        HttpClient(CIO) {
            install(WebSockets)
        }

    private val running = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)

    override fun connectAndStream(
        symbol: String,
        timeframe: Timeframe,
        scope: CoroutineScope,
    ): Flow<CandleEntity> =
        channelFlow {
            val interval = timeframe.toBybitInterval()
            val topic = "kline.$interval.$symbol"

            running.set(true)

            while (running.get() && isActive) {
                try {
                    log.info { "Connecting to ByBit WebSocket: ${config.websocketUrl}" }

                    client.webSocket(config.websocketUrl) {
                        reconnectAttempts.set(0)
                        log.info { "WebSocket connected to ByBit for $symbol ${timeframe.label}" }

                        // Subscribe to kline topic
                        val subscribeMsg = """{"op": "subscribe", "args": ["$topic"]}"""
                        send(subscribeMsg)
                        log.info { "Subscribed to topic: $topic" }

                        // Start heartbeat coroutine (ByBit requires ping every 20s)
                        val heartbeatJob =
                            launch {
                                while (isActive) {
                                    delay(config.heartbeatIntervalMs)
                                    try {
                                        send("""{"op": "ping"}""")
                                        log.debug { "Sent ping to ByBit" }
                                    } catch (e: Exception) {
                                        log.warn { "Failed to send ping: ${e.message}" }
                                        break
                                    }
                                }
                            }

                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()

                                        // Skip pong responses and subscription confirmations
                                        if (text.contains("\"op\":\"pong\"") || text.contains("\"op\":\"subscribe\"")) {
                                            log.debug { "Received: $text" }
                                            continue
                                        }

                                        val candle = parseKlineMessage(text, symbol, timeframe)
                                        if (candle != null) {
                                            send(candle)
                                        }
                                    }
                                    is Frame.Close -> {
                                        log.warn { "WebSocket closed" }
                                        break
                                    }
                                    else -> { /* ignore binary */ }
                                }
                            }
                        } finally {
                            heartbeatJob.cancel()
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
     * Parse ByBit kline WebSocket message.
     * Returns CandleEntity only when candle is closed (confirm = true).
     *
     * ByBit kline message format:
     * {
     *   "topic": "kline.1.BTCUSDT",
     *   "data": [{
     *     "start": 1672052400000,
     *     "end": 1672052459999,
     *     "interval": "1",
     *     "open": "16649.5",
     *     "close": "16650",
     *     "high": "16651",
     *     "low": "16649",
     *     "volume": "123.456",
     *     "turnover": "2054321.12",
     *     "confirm": true,
     *     "timestamp": 1672052460000
     *   }]
     * }
     */
    private fun parseKlineMessage(
        json: String,
        symbol: String,
        timeframe: Timeframe,
    ): CandleEntity? {
        return try {
            val node = objectMapper.readTree(json)

            // Check if this is a kline message
            val topic = node["topic"]?.asText() ?: return null
            if (!topic.startsWith("kline.")) return null

            val dataArray = node["data"] ?: return null
            if (!dataArray.isArray || dataArray.isEmpty) return null

            val kline = dataArray[0]

            // Only emit completed candles (confirm = true)
            val isConfirmed = kline["confirm"]?.asBoolean() ?: false
            if (!isConfirmed) return null

            CandleEntity(
                symbol = symbol,
                timeframe = timeframe,
                openTime = Instant.ofEpochMilli(kline["start"].asLong()),
                open = BigDecimal(kline["open"].asText()),
                high = BigDecimal(kline["high"].asText()),
                low = BigDecimal(kline["low"].asText()),
                close = BigDecimal(kline["close"].asText()),
                volume = BigDecimal(kline["volume"].asText()),
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
