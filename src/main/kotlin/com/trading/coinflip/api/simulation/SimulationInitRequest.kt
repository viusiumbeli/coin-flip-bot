package com.trading.coinflip.api.simulation

data class SimulationInitRequest(
    val symbol: String,
    val timeframe: String,
    val startDate: String? = null,
    val endDate: String? = null,
)
