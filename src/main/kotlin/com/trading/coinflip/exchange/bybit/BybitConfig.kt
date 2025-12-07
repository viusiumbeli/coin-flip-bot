package com.trading.coinflip.exchange.bybit

import com.trading.coinflip.exchange.ExchangeRestConfig
import com.trading.coinflip.exchange.ExchangeWebSocketConfig

/**
 * ByBit REST API configuration.
 */
data class BybitRestConfig(
    override val baseUrl: String = "https://api.bybit.com",
    override val httpTimeoutMs: Long = 30000,
    override val rateLimitDelayMs: Long = 50, // ByBit: 600 req/5s = 120/s, ~8ms between requests
) : ExchangeRestConfig

/**
 * ByBit WebSocket configuration for Linear (USDT) perpetuals.
 */
data class BybitWebSocketConfig(
    override val websocketUrl: String = "wss://stream.bybit.com/v5/public/linear",
    override val heartbeatIntervalMs: Long = 20000, // ByBit requires ping every 20s
    override val reconnectDelayMs: Long = 5000,
    override val maxReconnectAttempts: Int = 10,
) : ExchangeWebSocketConfig
