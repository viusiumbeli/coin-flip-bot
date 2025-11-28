package com.trading.coinflip.trading

import java.math.BigDecimal

/**
 * Immutable configuration for trading operations.
 * Contains parameters needed for position sizing, stops, and entry decisions.
 */
data class TradingConfig(
    val atrMultiplier: BigDecimal,
    val riskPerTrade: BigDecimal,
    val maxConcurrentPositions: Int,
    val transactionCostPercent: BigDecimal,
    val entryFrequency: Double,
)
