package com.trading.coinflip.exchange.deribit

import com.trading.coinflip.exchange.ExchangeRestConfig
import com.trading.coinflip.exchange.ExchangeWebSocketConfig

/**
 * Deribit REST API configuration.
 */
data class DeribitRestConfig(
    override val baseUrl: String = "https://www.deribit.com/api/v2",
    override val httpTimeoutMs: Long = 30000,
    override val rateLimitDelayMs: Long = 1000, // Deribit: conservative rate limiting
) : ExchangeRestConfig

/**
 * Deribit WebSocket configuration.
 */
data class DeribitWebSocketConfig(
    override val websocketUrl: String = "wss://www.deribit.com/ws/api/v2",
    override val heartbeatIntervalMs: Long = 15000, // Deribit heartbeat interval
    override val reconnectDelayMs: Long = 5000,
    override val maxReconnectAttempts: Int = 10,
) : ExchangeWebSocketConfig
