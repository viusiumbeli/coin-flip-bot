package com.trading.coinflip.api.backtest

import com.trading.coinflip.common.model.Timeframe
import java.time.Instant

data class BacktestRequest(
    val symbol: String,
    val timeframe: Timeframe,
    val startDate: Instant,
    val endDate: Instant,
)
