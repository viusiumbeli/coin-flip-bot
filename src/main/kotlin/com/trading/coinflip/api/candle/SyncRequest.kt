package com.trading.coinflip.api.candle

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.Exchange

data class SyncRequest(
    val symbol: String,
    val timeframe: Timeframe,
    val exchange: Exchange? = null,
)
