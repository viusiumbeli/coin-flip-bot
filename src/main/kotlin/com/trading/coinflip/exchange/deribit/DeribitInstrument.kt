package com.trading.coinflip.exchange.deribit

import java.time.Instant

/**
 * DTO representing a Deribit option instrument.
 */
data class DeribitInstrument(
    val instrumentName: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val strike: Double,
    val optionType: String, // "call" or "put"
    val expirationTimestamp: Instant,
    val isActive: Boolean,
)
