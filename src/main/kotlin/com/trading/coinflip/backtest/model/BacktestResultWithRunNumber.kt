package com.trading.coinflip.backtest.model

/**
 * Data class for backtest result with run number.
 */
data class BacktestResultWithRunNumber(
    val result: BacktestResult,
    val runNumber: Int,
)