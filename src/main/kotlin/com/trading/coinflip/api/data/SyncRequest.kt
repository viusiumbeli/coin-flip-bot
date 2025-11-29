package com.trading.coinflip.api.data

import com.trading.coinflip.common.model.Timeframe

data class SyncRequest(
    val symbol: String,
    val timeframe: Timeframe,
)
