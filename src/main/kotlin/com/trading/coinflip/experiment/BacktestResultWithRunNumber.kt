package com.trading.coinflip.experiment

import com.trading.coinflip.common.model.BacktestResult

/**
 * Data class for backtest result with run number.
 */
data class BacktestResultWithRunNumber(
    val result: BacktestResult,
    val runNumber: Int,
)
