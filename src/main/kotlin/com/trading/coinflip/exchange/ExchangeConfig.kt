package com.trading.coinflip.exchange

/**
 * Configuration for exchange REST API client.
 * Each exchange provides its own implementation.
 */
interface ExchangeRestConfig {
    val baseUrl: String
    val httpTimeoutMs: Long
    val rateLimitDelayMs: Long
}

/**
 * Configuration for exchange WebSocket client.
 * Each exchange provides its own implementation.
 */
interface ExchangeWebSocketConfig {
    val websocketUrl: String
    val heartbeatIntervalMs: Long
    val reconnectDelayMs: Long
    val maxReconnectAttempts: Int
}
