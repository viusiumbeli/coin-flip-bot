package com.trading.coinflip.exchange.binance

import com.trading.coinflip.exchange.ExchangeRestConfig
import com.trading.coinflip.exchange.ExchangeWebSocketConfig

/**
 * Binance REST API configuration.
 */
data class BinanceRestConfig(
    override val baseUrl: String = "https://api.binance.com",
    override val httpTimeoutMs: Long = 30000,
    override val rateLimitDelayMs: Long = 0,
) : ExchangeRestConfig

/**
 * Binance WebSocket configuration.
 */
data class BinanceWebSocketConfig(
    override val websocketUrl: String = "wss://stream.binance.com:9443/ws",
    override val heartbeatIntervalMs: Long = 30000,
    override val reconnectDelayMs: Long = 5000,
    override val maxReconnectAttempts: Int = 10,
) : ExchangeWebSocketConfig
