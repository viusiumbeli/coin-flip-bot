package com.trading.coinflip.api.simulation

import com.trading.coinflip.common.model.Timeframe
import java.time.Instant

data class SimulationInitRequest(
    val symbol: String,
    val timeframe: Timeframe,
    val startDate: Instant,
    val endDate: Instant,
)
