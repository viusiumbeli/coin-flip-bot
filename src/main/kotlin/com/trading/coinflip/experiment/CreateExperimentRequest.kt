package com.trading.coinflip.experiment

import com.trading.coinflip.common.model.Timeframe
import java.time.Instant

data class CreateExperimentRequest(
    val symbol: String,
    val timeframe: Timeframe,
    val startDate: Instant,
    val endDate: Instant,
    val numBacktests: Int = 1,
    val customName: String? = null,
    val notes: String? = null,
)
