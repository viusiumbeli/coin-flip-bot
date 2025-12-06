package com.trading.coinflip.candle

import java.time.Instant

data class CandleStats(
    val symbol: String,
    val timeframe: String,
    val candleCount: Long,
    val earliest: Instant?,
    val latest: Instant?,
)
