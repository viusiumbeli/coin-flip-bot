package com.trading.coinflip.data

data class SyncRequest(
    val symbol: String,
    val timeframe: String,
)
