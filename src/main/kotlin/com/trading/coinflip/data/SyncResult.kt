package com.trading.coinflip.data

data class SyncResult(
    val symbol: String,
    val timeframe: String,
    val newCandlesAdded: Int,
    val success: Boolean,
    val error: String? = null,
)
