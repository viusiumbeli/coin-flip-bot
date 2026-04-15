package com.trading.coinflip.data

data class AvailableSymbolsResponse(
    val symbols: List<String>,
    val timeframes: List<String>,
)
