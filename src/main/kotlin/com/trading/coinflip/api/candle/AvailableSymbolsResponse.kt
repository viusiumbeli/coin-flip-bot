package com.trading.coinflip.api.candle

data class AvailableSymbolsResponse(
    val symbols: List<String>,
    val timeframes: List<String>,
)
