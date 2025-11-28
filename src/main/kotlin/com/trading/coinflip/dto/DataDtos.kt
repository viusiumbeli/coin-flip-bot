package com.trading.coinflip.dto

import java.time.Instant

data class DataStatus(
    val symbol: String,
    val timeframe: String,
    val candleCount: Long,
    val earliestCandle: Instant?,
    val latestCandle: Instant?,
    val hoursOutdated: Long?,
)

data class SyncRequest(
    val symbol: String,
    val timeframe: String,
)

data class SyncResult(
    val symbol: String,
    val timeframe: String,
    val newCandlesAdded: Int,
    val success: Boolean,
    val error: String? = null,
)
