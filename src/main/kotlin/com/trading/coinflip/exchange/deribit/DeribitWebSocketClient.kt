package com.trading.coinflip.exchange.deribit

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.ExchangeWebSocketClient
import com.trading.coinflip.exchange.ExchangeWebSocketConfig
import com.trading.coinflip.exchange.deribit.DeribitClient.Companion.toDeribitResolution
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
 * Deribit WebSocket client for real-time kline streaming.
 * Implements ExchangeWebSocketClient interface for exchange abstraction.
 *
 * Deribit WebSocket characteristics:
 * - URL: wss://www.deribit.com/ws/api/v2
 * - Subscribe via JSON-RPC: {"jsonrpc":"2.0","method":"public/subscribe","params":{"channels":[...]}}
 * - Channel: chart.trades.{instrument}.{resolution}
 * - Heartbeat: set_heartbeat + respond to test requests
 * - No explicit candle close flag: detect by tick shift to next period
 */
class DeribitWebSocketClient(
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
            val resolution = timeframe.toDeribitResolution()
            val channel = "chart.trades.$symbol.$resolution"
            val candlePeriodMs = timeframe.minutes * 60 * 1000L

            running.set(true)

            // Buffer to detect candle close by tick shift
            var bufferedCandle: CandleEntity? = null

            while (running.get() && isActive) {
                try {
                    log.info { "Connecting to Deribit WebSocket: ${config.websocketUrl}" }

                    client.webSocket(config.websocketUrl) {
                        reconnectAttempts.set(0)
                        log.info { "WebSocket connected to Deribit for $symbol ${timeframe.label}" }

                        // Set heartbeat
                        val interval = config.heartbeatIntervalMs / 1000
                        val heartbeatMsg =
                            """{"jsonrpc":"2.0","id":1,"method":"public/set_heartbeat","params":{"interval":$interval}}"""
                        send(heartbeatMsg)

                        // Subscribe to chart trades channel
                        val subscribeMsg =
                            """{"jsonrpc":"2.0","id":2,"method":"public/subscribe","params":{"channels":["$channel"]}}"""
                        send(subscribeMsg)
                        log.info { "Subscribed to channel: $channel" }

                        // Heartbeat response coroutine
                        val heartbeatJob =
                            launch {
                                // No periodic ping needed — Deribit sends heartbeat requests to us
                                // We handle them in the message loop below
                                delay(Long.MAX_VALUE)
                            }

                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        val node = objectMapper.readTree(text)

                                        // Handle heartbeat test request from server
                                        val method = node["method"]?.asText()
                                        if (method == "heartbeat") {
                                            val type = node["params"]?.get("type")?.asText()
                                            if (type == "test_request") {
                                                send("""{"jsonrpc":"2.0","id":0,"method":"public/test"}""")
                                                log.debug { "Responded to Deribit heartbeat test_request" }
                                            }
                                            continue
                                        }

                                        // Skip subscription confirmations and other responses
                                        if (node.has("id")) {
                                            log.debug { "Received response: $text" }
                                            continue
                                        }

                                        // Parse subscription notification
                                        if (method != "subscription") continue
                                        val params = node["params"] ?: continue
                                        val paramChannel = params["channel"]?.asText() ?: continue
                                        if (paramChannel != channel) continue

                                        val data = params["data"] ?: continue
                                        val tick = data["tick"]?.asLong() ?: continue

                                        // Align tick to candle open time
                                        val candleOpenMs = (tick / candlePeriodMs) * candlePeriodMs
                                        val candleOpen = Instant.ofEpochMilli(candleOpenMs)

                                        val currentCandle =
                                            CandleEntity(
                                                symbol = symbol,
                                                timeframe = timeframe,
                                                openTime = candleOpen,
                                                open = BigDecimal(data["open"].asText()),
                                                high = BigDecimal(data["high"].asText()),
                                                low = BigDecimal(data["low"].asText()),
                                                close = BigDecimal(data["close"].asText()),
                                                volume = BigDecimal(data["volume"].asText()),
                                            )

                                        // Detect candle close: if tick moved to a new period
                                        val prev = bufferedCandle
                                        if (prev != null && prev.openTime != currentCandle.openTime) {
                                            // Previous candle is now closed — emit it
                                            send(prev)
                                            log.debug {
                                                "Emitted closed candle: ${prev.symbol} ${prev.openTime}"
                                            }
                                        }

                                        // Buffer current candle (always the latest update)
                                        bufferedCandle = currentCandle
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

    override fun stop() {
        running.set(false)
    }

    override fun isRunning(): Boolean = running.get()

    override fun getReconnectAttempts(): Int = reconnectAttempts.get()
}
