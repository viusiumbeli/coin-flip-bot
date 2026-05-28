package com.trading.coinflip.api.candle

data class DeribitInstrumentResponse(
    val instrumentName: String,
    val baseCurrency: String,
    val strike: Double,
    val optionType: String,
    val expirationTimestamp: String,
    val isActive: Boolean,
)
