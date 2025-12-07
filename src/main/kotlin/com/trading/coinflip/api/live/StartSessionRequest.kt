package com.trading.coinflip.api.live

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.Exchange

data class StartSessionRequest(
    val symbol: String,
    val timeframe: Timeframe,
    val exchange: Exchange? = null, // Defaults to configured exchange if null
)
