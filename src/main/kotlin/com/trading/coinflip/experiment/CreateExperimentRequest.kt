package com.trading.coinflip.experiment

data class CreateExperimentRequest(
    val symbol: String,
    val timeframe: String,
    val startDate: String,
    val endDate: String,
    val numBacktests: Int = 1,
    val customName: String? = null,
    val notes: String? = null,
)
