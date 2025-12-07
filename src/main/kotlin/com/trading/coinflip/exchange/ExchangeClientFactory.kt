package com.trading.coinflip.exchange

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.config.LiveProperties
import com.trading.coinflip.exchange.binance.BinanceClient
import com.trading.coinflip.exchange.binance.BinanceRestConfig
import com.trading.coinflip.exchange.binance.BinanceWebSocketClient
import com.trading.coinflip.exchange.binance.BinanceWebSocketConfig
import com.trading.coinflip.exchange.bybit.BybitClient
import com.trading.coinflip.exchange.bybit.BybitRestConfig
import com.trading.coinflip.exchange.bybit.BybitWebSocketClient
import com.trading.coinflip.exchange.bybit.BybitWebSocketConfig
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating exchange clients based on configuration.
 * Supports both global default exchange and per-session exchange selection.
 * Caches clients per exchange type for efficient reuse across sessions.
 */
@Component
class ExchangeClientFactory(
    private val objectMapper: ObjectMapper,
    private val liveProperties: LiveProperties,
    private val backtestProperties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    // Cached clients per exchange - allows multiple exchanges to run simultaneously
    private val restClients = ConcurrentHashMap<Exchange, ExchangeClient>()
    private val webSocketClients = ConcurrentHashMap<Exchange, ExchangeWebSocketClient>()

    /**
     * Get REST API client for a specific exchange.
     * Creates and caches client if not already present.
     */
    fun getRestClient(exchange: Exchange): ExchangeClient =
        restClients.computeIfAbsent(exchange) {
            log.info { "Creating REST client for exchange: $exchange" }
            when (exchange) {
                Exchange.BINANCE -> createBinanceRestClient()
                Exchange.BYBIT -> createBybitRestClient()
            }
        }

    /**
     * Get REST API client for the configured default exchange.
     */
    fun getRestClient(): ExchangeClient = getRestClient(liveProperties.exchange)

    /**
     * Get WebSocket client for a specific exchange.
     * Creates and caches client if not already present.
     */
    fun getWebSocketClient(exchange: Exchange): ExchangeWebSocketClient =
        webSocketClients.computeIfAbsent(exchange) {
            log.info { "Creating WebSocket client for exchange: $exchange" }
            when (exchange) {
                Exchange.BINANCE -> createBinanceWebSocketClient()
                Exchange.BYBIT -> createBybitWebSocketClient()
            }
        }

    /**
     * Get WebSocket client for the configured default exchange.
     */
    fun getWebSocketClient(): ExchangeWebSocketClient = getWebSocketClient(liveProperties.exchange)

    /**
     * Get the default configured exchange.
     */
    fun getExchange(): Exchange = liveProperties.exchange

    /**
     * Invalidate cached clients for a specific exchange.
     */
    fun invalidateClients(exchange: Exchange) {
        log.info { "Invalidating cached clients for exchange: $exchange" }
        restClients.remove(exchange)
        webSocketClients.remove(exchange)?.stop()
    }

    /**
     * Invalidate all cached clients.
     */
    fun invalidateAllClients() {
        log.info { "Invalidating all cached exchange clients" }
        restClients.clear()
        webSocketClients.values.forEach { it.stop() }
        webSocketClients.clear()
    }

    // --- Binance client creation ---

    private fun createBinanceRestClient(): BinanceClient {
        val config =
            BinanceRestConfig(
                baseUrl = liveProperties.binanceRestUrl,
                httpTimeoutMs = backtestProperties.api.httpTimeoutMs,
                rateLimitDelayMs = backtestProperties.api.rateLimitDelayMs,
            )
        return BinanceClient(objectMapper, config)
    }

    private fun createBinanceWebSocketClient(): BinanceWebSocketClient {
        val config =
            BinanceWebSocketConfig(
                websocketUrl = liveProperties.binanceWebsocketUrl,
                heartbeatIntervalMs = liveProperties.heartbeatIntervalMs,
                reconnectDelayMs = liveProperties.reconnectDelayMs,
                maxReconnectAttempts = liveProperties.maxReconnectAttempts,
            )
        return BinanceWebSocketClient(objectMapper, config)
    }

    // --- ByBit client creation ---

    private fun createBybitRestClient(): BybitClient {
        val config =
            BybitRestConfig(
                baseUrl = liveProperties.bybitRestUrl,
                httpTimeoutMs = backtestProperties.api.httpTimeoutMs,
                rateLimitDelayMs = backtestProperties.api.rateLimitDelayMs.coerceAtLeast(50),
            )
        return BybitClient(objectMapper, config)
    }

    private fun createBybitWebSocketClient(): BybitWebSocketClient {
        val config =
            BybitWebSocketConfig(
                websocketUrl = liveProperties.bybitWebsocketUrl,
                heartbeatIntervalMs = 20000,
                reconnectDelayMs = liveProperties.reconnectDelayMs,
                maxReconnectAttempts = liveProperties.maxReconnectAttempts,
            )
        return BybitWebSocketClient(objectMapper, config)
    }
}
