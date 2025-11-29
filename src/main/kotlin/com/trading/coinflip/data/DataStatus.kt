package com.trading.coinflip.data

import java.time.Instant

data class DataStatus(
    val symbol: String,
    val timeframe: String,
    val candleCount: Long,
    val earliestCandle: Instant?,
    val latestCandle: Instant?,
    val hoursOutdated: Long?,
)
