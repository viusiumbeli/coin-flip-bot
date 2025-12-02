package com.trading.coinflip.api.live

data class LiveTradesResponse(
    val trades: List<LiveTradeResponse>,
    val totalCount: Long,
)
