package com.trading.coinflip.api.candle

import java.time.Instant

data class CandleStatusResponse(
    val symbol: String,
    val timeframe: String,
    val candleCount: Long,
    val earliestCandle: Instant?,
    val latestCandle: Instant?,
    val hoursOutdated: Long?,
)
