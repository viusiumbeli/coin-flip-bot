package com.trading.coinflip.api.candle

import com.trading.coinflip.common.model.Timeframe

data class SyncRequest(
    val symbol: String,
    val timeframe: Timeframe,
)
