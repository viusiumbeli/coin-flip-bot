package com.trading.coinflip.common.config

import com.trading.coinflip.exchange.Exchange
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@ConfigurationProperties(prefix = "live")
data class LiveProperties(
    var enabled: Boolean = false,
    var symbols: List<String> = listOf("BTCUSDT"),
    var initialCapital: BigDecimal = BigDecimal(10000),
    var prefetchCandleCount: Int = 20,
    var balanceSnapshotIntervalMinutes: Int = 60,
    var exchange: Exchange = Exchange.BINANCE,
    var reconnectDelayMs: Long = 5000,
    var maxReconnectAttempts: Int = 10,
    var heartbeatIntervalMs: Long = 30000,
    var binanceWebsocketUrl: String = "wss://stream.binance.com:9443/ws",
    var binanceRestUrl: String = "https://api.binance.com",
    var bybitWebsocketUrl: String = "wss://stream.bybit.com/v5/public/linear",
    var bybitRestUrl: String = "https://api.bybit.com",
)
