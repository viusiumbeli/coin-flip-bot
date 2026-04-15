package com.trading.coinflip.backtest

data class BacktestRequest(
    val symbol: String,
    val timeframe: String,
    val startDate: String? = null,
    val endDate: String? = null,
)
