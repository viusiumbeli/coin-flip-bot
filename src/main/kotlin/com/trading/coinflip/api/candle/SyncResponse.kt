package com.trading.coinflip.api.candle

data class SyncResponse(
    val symbol: String,
    val timeframe: String,
    val newCandlesAdded: Int,
    val success: Boolean,
    val error: String? = null,
)
