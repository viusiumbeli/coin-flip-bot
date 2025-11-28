package com.trading.coinflip.config

import com.trading.coinflip.model.Timeframe
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.Instant

@Configuration
@ConfigurationProperties(prefix = "backtest")
data class BacktestProperties(
    // Trading parameters
    var symbols: List<String> = listOf("BTCUSDT"),
    var timeframes: List<Timeframe> = listOf(Timeframe.ONE_HOUR),
    var initialCapital: BigDecimal = BigDecimal(10000),
    var riskPerTrade: BigDecimal = BigDecimal(1.0),
    var atrPeriod: Int = 10,
    var atrMultiplier: BigDecimal = BigDecimal(3.0),
    var transactionCostPercent: BigDecimal = BigDecimal(0.1),
    var maxConcurrentPositions: Int = 5,
    var startDate: Instant? = null,
    var endDate: Instant? = null,

    // Strategy parameters
    var entryFrequency: Double = 0.1,

    // Experiment limits
    var syncBacktestLimit: Int = 1_000_000,
    var asyncBacktestLimit: Int = 10_000_000,
    var tradesThreshold: Int = 100,

    // Async execution parameters
    var parallelismMin: Int = 4,
    var parallelismMax: Int = 32,
    var channelCapacity: Int = 1000,
    var batchSize: Int = 1000,
    var shutdownTimeoutMs: Long = 30_000,
    var progressLogInterval: Int = 10_000,

    // API parameters
    var maxPageSize: Int = 1000,
    var httpTimeoutMs: Long = 30_000,
    var rateLimitDelayMs: Long = 100
)
