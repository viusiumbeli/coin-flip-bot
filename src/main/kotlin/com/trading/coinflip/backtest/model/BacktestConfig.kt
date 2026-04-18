package com.trading.coinflip.backtest.model

import com.trading.coinflip.common.config.TradingConfig
import com.trading.coinflip.common.model.Timeframe
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