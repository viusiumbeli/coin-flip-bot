package com.trading.coinflip.simulation

import java.math.BigDecimal

data class SimulationMetricsDto(
    val accountBalance: BigDecimal = BigDecimal.ZERO,
    val peakBalance: BigDecimal = BigDecimal.ZERO,
    val drawdown: BigDecimal = BigDecimal.ZERO,
    val drawdownPercent: BigDecimal = BigDecimal.ZERO,
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val winRate: BigDecimal = BigDecimal.ZERO,
    val openPositions: Int = 0,
    val allocatedCapital: BigDecimal = BigDecimal.ZERO,
    val availableCapital: BigDecimal = BigDecimal.ZERO,
)
