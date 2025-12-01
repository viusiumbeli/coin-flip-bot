package com.trading.coinflip.common.config

import java.math.BigDecimal
import java.math.RoundingMode

data class TradingConfig(
    var riskPerTrade: BigDecimal = BigDecimal(1.0),
    var atrPeriod: Int = 10,
    var atrMultiplier: BigDecimal = BigDecimal(3.0),
    var transactionCostPercent: BigDecimal = BigDecimal(0.1),
    var maxConcurrentPositions: Int = 5,
    var entryFrequency: Double = 0.1,
) {
    // Pre-computed rates for hot path performance (avoid BigDecimal division per call)
    val riskPerTradeRate: BigDecimal by lazy {
        riskPerTrade.divide(HUNDRED, 8, RoundingMode.HALF_UP)
    }
    val roundTripTransactionCostRate: BigDecimal by lazy {
        transactionCostPercent.divide(HUNDRED, 8, RoundingMode.HALF_UP) * TWO
    }

    companion object {
        private val HUNDRED = BigDecimal(100)
        private val TWO = BigDecimal(2)
    }
}
