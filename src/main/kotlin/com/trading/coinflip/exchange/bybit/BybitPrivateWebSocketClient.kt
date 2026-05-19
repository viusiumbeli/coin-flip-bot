package com.trading.coinflip.exchange.bybit

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.exchange.ExchangeExecutionClient
import com.trading.coinflip.exchange.ExchangeWebSocketConfig
import com.trading.coinflip.exchange.ExecutionEvent
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * ByBit Private WebSocket client for execution and position updates.
 * Connects to wss://stream.bybit.com/v5/private and authenticates.
 * Subscribes to execution.linear and position topics to detect position closures.
 */
class BybitPrivateWebSocketClient(
    private val objectMapper: ObjectMapper,
    private val authenticator: BybitAuthenticator,
    private val config: ExchangeWebSocketConfig,
) : ExchangeExecutionClient {
    private val log = KotlinLogging.logger {}

    private val client =
        HttpClient(CIO) {
            install(WebSockets)
        }

    private val running = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)

    override fun connectAndStream(scope: CoroutineScope): Flow<ExecutionEvent> =
        channelFlow {
            running.set(true)

            while (running.get() && isActive) {
                try {
                    log.info { "Connecting to ByBit Private WebSocket: ${config.websocketUrl}" }

                    client.webSocket(config.websocketUrl) {
                        reconnectAttempts.set(0)
                        log.info { "Private WebSocket connected" }

                        // Authenticate
                        val (apiKey, expires, signature) = authenticator.generateWebSocketAuth()
                        val authMsg = """{"req_id": "auth", "op": "auth", "args": ["$apiKey", $expires, "$signature"]}"""
                        send(authMsg)
                        log.info { "Sent authentication request" }

                        // Wait for auth response
                        var authenticated = false
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                if (text.contains("\"op\":\"auth\"")) {
                                    if (text.contains("\"success\":true")) {
                                        authenticated = true
                                        log.info { "Authentication successful" }
                                        break
                                    } else {
                                        log.error { "Authentication failed: $text" }
                                        throw RuntimeException("WebSocket authentication failed")
                                    }
                                }
                            }
                        }

                        if (!authenticated) {
                            throw RuntimeException("No auth response received")
                        }

                        // Subscribe to execution and position topics
                        val subscribeMsg = """{"op": "subscribe", "args": ["execution.linear", "position.linear"]}"""
                        send(subscribeMsg)
                        log.info { "Subscribed to execution.linear and position.linear" }

                        // Start heartbeat coroutine
                        val heartbeatJob =
                            launch {
                                while (isActive) {
                                    delay(config.heartbeatIntervalMs)
                                    try {
                                        send("""{"op": "ping"}""")
                                        log.debug { "Sent ping to private WebSocket" }
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

                                        // Skip pong and subscribe responses
                                        if (text.contains("\"op\":\"pong\"") || text.contains("\"op\":\"subscribe\"")) {
                                            log.debug { "Received: $text" }
                                            continue
                                        }

                                        // Parse execution events
                                        val events = parseMessage(text)
                                        for (event in events) {
                                            send(event)
                                        }
                                    }
                                    is Frame.Close -> {
                                        log.warn { "Private WebSocket closed" }
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
                    log.info { "Private WebSocket connection cancelled" }
                    throw e
                } catch (e: Exception) {
                    log.error(e) { "Private WebSocket error" }

                    if (!running.get()) break

                    val attempts = reconnectAttempts.incrementAndGet()
                    if (attempts > config.maxReconnectAttempts) {
                        log.error { "Max reconnect attempts ($attempts) reached, stopping" }
                        break
                    }

                    // Exponential backoff
                    val delayMs = config.reconnectDelayMs * (1L shl (attempts - 1).coerceAtMost(6))
                    log.info { "Reconnecting in ${delayMs}ms (attempt $attempts)" }
                    delay(delayMs)
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Parse execution and position messages from ByBit private WebSocket.
     */
    private fun parseMessage(json: String): List<ExecutionEvent> {
        return try {
            val node = objectMapper.readTree(json)
            val topic = node["topic"]?.asText() ?: return emptyList()

            val dataArray = node["data"] ?: return emptyList()
            if (!dataArray.isArray) return emptyList()

            when {
                topic.startsWith("execution") -> parseExecutionEvents(dataArray)
                topic.startsWith("position") -> parsePositionEvents(dataArray)
                else -> emptyList()
            }
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse private message: $json" }
            emptyList()
        }
    }

    /**
     * Parse execution events - detect position closures.
     */
    private fun parseExecutionEvents(dataArray: com.fasterxml.jackson.databind.JsonNode): List<ExecutionEvent> {
        val events = mutableListOf<ExecutionEvent>()

        for (exec in dataArray) {
            val closedSize = exec["closedSize"]?.asText()?.let { BigDecimal(it) } ?: BigDecimal.ZERO

            // Only emit if position was closed
            if (closedSize > BigDecimal.ZERO) {
                val event =
                    ExecutionEvent.PositionClosed(
                        symbol = exec["symbol"]?.asText() ?: "",
                        side = exec["side"]?.asText() ?: "",
                        closedSize = closedSize,
                        execPrice = exec["execPrice"]?.asText()?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
                        execPnl = exec["execPnl"]?.asText()?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
                        orderId = exec["orderId"]?.asText() ?: "",
                        execId = exec["execId"]?.asText() ?: "",
                    )
                log.info { "Position closed on exchange: ${event.symbol} ${event.side} qty=${event.closedSize} pnl=${event.execPnl}" }
                events.add(event)
            }
        }

        return events
    }

    /**
     * Parse position updates.
     */
    private fun parsePositionEvents(dataArray: com.fasterxml.jackson.databind.JsonNode): List<ExecutionEvent> {
        val events = mutableListOf<ExecutionEvent>()

        for (pos in dataArray) {
            val size = pos["size"]?.asText()?.let { BigDecimal(it) } ?: BigDecimal.ZERO
            val side = pos["side"]?.asText() ?: ""

            val event =
                ExecutionEvent.PositionUpdate(
                    symbol = pos["symbol"]?.asText() ?: "",
                    side = side,
                    size = size,
                    entryPrice = pos["entryPrice"]?.asText()?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
                    unrealisedPnl = pos["unrealisedPnl"]?.asText()?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
                    curRealisedPnl = pos["curRealisedPnl"]?.asText()?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
                    positionIdx = pos["positionIdx"]?.asInt() ?: 0,
                )

            // Log position closure (size = 0)
            if (size == BigDecimal.ZERO) {
                log.info { "Position fully closed on exchange: ${event.symbol}" }
            }

            events.add(event)
        }

        return events
    }

    override fun stop() {
        running.set(false)
    }

    override fun isRunning(): Boolean = running.get()
}
