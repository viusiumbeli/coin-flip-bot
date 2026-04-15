package com.trading.coinflip.api.data

data class SyncRequest(
    val symbol: String,
    val timeframe: String,
)
