package com.trading.coinflip.common.model

import com.trading.coinflip.common.config.TradingConfig
import java.math.BigDecimal
import java.time.Instant

data class BacktestConfig(
    val symbol: String,
    val timeframe: Timeframe,
    val initialCapital: BigDecimal,
    val trading: TradingConfig,
    val startDate: Instant? = null,
    val endDate: Instant? = null,
)
