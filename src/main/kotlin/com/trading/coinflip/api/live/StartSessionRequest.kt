package com.trading.coinflip.api.live

import com.trading.coinflip.common.model.Timeframe

data class StartSessionRequest(
    val symbol: String,
    val timeframe: Timeframe,
)
