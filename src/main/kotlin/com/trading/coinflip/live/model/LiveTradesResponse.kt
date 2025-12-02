package com.trading.coinflip.live.model

data class LiveTradesResponse(
    val trades: List<LiveTradeDto>,
    val totalCount: Long,
)
