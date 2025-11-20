package com.trading.coinflip.config

import com.trading.coinflip.model.Timeframe
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.Instant

@Configuration
@ConfigurationProperties(prefix = "backtest")
data class BacktestProperties(
    var symbols: List<String> = listOf("BTCUSDT"),
    var timeframes: List<Timeframe> = listOf(Timeframe.ONE_HOUR),
    var initialCapital: BigDecimal = BigDecimal(10000),
    var riskPerTrade: BigDecimal = BigDecimal(1.0),
    var atrPeriod: Int = 10,
    var atrMultiplier: BigDecimal = BigDecimal(3.0),
    var transactionCostPercent: BigDecimal = BigDecimal(0.1),
    var maxConcurrentPositions: Int = 5,
    var startDate: Instant? = null,
    var endDate: Instant? = null
)
