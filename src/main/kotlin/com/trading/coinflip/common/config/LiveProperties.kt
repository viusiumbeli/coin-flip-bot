package com.trading.coinflip.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@ConfigurationProperties(prefix = "live")
data class LiveProperties(
    var enabled: Boolean = false,
    var symbols: List<String> = listOf("BTCUSDT"),
    var initialCapital: BigDecimal = BigDecimal(10000),
    var reconnectDelayMs: Long = 5000,
    var maxReconnectAttempts: Int = 10,
    var balanceSnapshotIntervalMinutes: Int = 60,
    var heartbeatIntervalMs: Long = 30000,
    var websocketUrl: String = "wss://stream.binance.com:9443/ws",
)