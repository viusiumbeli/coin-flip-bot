package com.trading.coinflip.api.backtest

data class BacktestRequest(
    val symbol: String,
    val timeframe: String,
    val startDate: String? = null,
    val endDate: String? = null,
)
