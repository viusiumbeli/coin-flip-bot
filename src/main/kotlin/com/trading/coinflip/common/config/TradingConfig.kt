package com.trading.coinflip.common.config

import java.math.BigDecimal

data class TradingConfig(
    var riskPerTrade: BigDecimal = BigDecimal(1.0),
    var atrPeriod: Int = 10,
    var atrMultiplier: BigDecimal = BigDecimal(3.0),
    var transactionCostPercent: BigDecimal = BigDecimal(0.1),
    var maxConcurrentPositions: Int = 5,
    var entryFrequency: Double = 0.1,
)
