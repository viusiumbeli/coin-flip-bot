package com.trading.coinflip.common.config

import com.trading.coinflip.common.model.Timeframe
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.Instant

@Configuration
@ConfigurationProperties(prefix = "backtest")
data class BacktestProperties(
    var symbols: List<String> = listOf("BTCUSDT"),
    var timeframes: List<Timeframe> = listOf(Timeframe.ONE_HOUR),
    var initialCapital: BigDecimal = BigDecimal(10000),
    var startDate: Instant? = null,
    var endDate: Instant? = null,
    @NestedConfigurationProperty
    var trading: TradingConfig = TradingConfig(),
    @NestedConfigurationProperty
    var experiment: ExperimentConfig = ExperimentConfig(),
    @NestedConfigurationProperty
    var async: AsyncConfig = AsyncConfig(),
    @NestedConfigurationProperty
    var api: ApiConfig = ApiConfig(),
)
