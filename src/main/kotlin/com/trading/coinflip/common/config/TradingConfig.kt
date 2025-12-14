package com.trading.coinflip.common.config

import java.math.BigDecimal
import java.math.RoundingMode

data class TradingConfig(
    var riskPerTrade: BigDecimal = BigDecimal(1.0),
    var atrPeriod: Int = 10,
    var atrMultiplier: BigDecimal = BigDecimal(3.0),
    var transactionCostPercent: BigDecimal = BigDecimal(0.1),
    var maxConcurrentPositions: Int = 1,
    var maxPositionSizePercent: BigDecimal = BigDecimal(20),
    var entryFrequency: Double = 0.1,
    var leverage: Int = 1, // Default 1x (no leverage)
) {
    val maxPositionSizeRate: BigDecimal by lazy {
        maxPositionSizePercent.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
    }
}
