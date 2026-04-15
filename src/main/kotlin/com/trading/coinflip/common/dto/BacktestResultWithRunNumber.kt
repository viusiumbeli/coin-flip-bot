package com.trading.coinflip.common.dto

import com.trading.coinflip.common.model.BacktestResult

/**
 * Data class for backtest result with run number.
 */
data class BacktestResultWithRunNumber(
    val result: BacktestResult,
    val runNumber: Int,
)
