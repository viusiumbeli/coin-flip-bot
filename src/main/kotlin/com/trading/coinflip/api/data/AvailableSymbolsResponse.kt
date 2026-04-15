package com.trading.coinflip.api.data

data class AvailableSymbolsResponse(
    val symbols: List<String>,
    val timeframes: List<String>,
)