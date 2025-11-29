package com.trading.coinflip.simulation

import java.math.BigDecimal

data class SimulationMetricsDto(
    val accountBalance: BigDecimal,
    val peakBalance: BigDecimal,
    val drawdown: BigDecimal,
    val drawdownPercent: BigDecimal,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: BigDecimal,
    val openPositions: Int,
    val allocatedCapital: BigDecimal,
    val availableCapital: BigDecimal,
)
