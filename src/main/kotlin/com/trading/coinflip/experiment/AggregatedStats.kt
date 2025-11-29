package com.trading.coinflip.experiment

import java.math.BigDecimal

/**
 * Aggregated statistics computed from multiple backtest runs.
 */
data class AggregatedStats(
    val finalCapital: BigDecimal,
    val totalReturn: BigDecimal,
    val totalReturnPercent: BigDecimal,
    val maxDrawdown: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val winRate: BigDecimal,
    val profitFactor: BigDecimal,
    val sharpeRatio: BigDecimal,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val averageWin: BigDecimal,
    val averageLoss: BigDecimal,
    val largestWin: BigDecimal,
    val largestLoss: BigDecimal,
    val averageTradeDuration: Long,
    val buyAndHoldReturn: BigDecimal,
    val buyAndHoldReturnPercent: BigDecimal,
    val runsBeatBuyHold: Int,
    // Variance/distribution metrics for totalReturnPercent
    val returnStdDev: BigDecimal,
    val returnMin: BigDecimal,
    val returnMax: BigDecimal,
    val returnP5: BigDecimal,
    val returnP25: BigDecimal,
    val returnP50: BigDecimal,
    val returnP75: BigDecimal,
    val returnP95: BigDecimal,
) {
    companion object {
        fun empty() =
            AggregatedStats(
                finalCapital = BigDecimal.ZERO,
                totalReturn = BigDecimal.ZERO,
                totalReturnPercent = BigDecimal.ZERO,
                maxDrawdown = BigDecimal.ZERO,
                maxDrawdownPercent = BigDecimal.ZERO,
                winRate = BigDecimal.ZERO,
                profitFactor = BigDecimal.ZERO,
                sharpeRatio = BigDecimal.ZERO,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                averageWin = BigDecimal.ZERO,
                averageLoss = BigDecimal.ZERO,
                largestWin = BigDecimal.ZERO,
                largestLoss = BigDecimal.ZERO,
                averageTradeDuration = 0,
                buyAndHoldReturn = BigDecimal.ZERO,
                buyAndHoldReturnPercent = BigDecimal.ZERO,
                runsBeatBuyHold = 0,
                returnStdDev = BigDecimal.ZERO,
                returnMin = BigDecimal.ZERO,
                returnMax = BigDecimal.ZERO,
                returnP5 = BigDecimal.ZERO,
                returnP25 = BigDecimal.ZERO,
                returnP50 = BigDecimal.ZERO,
                returnP75 = BigDecimal.ZERO,
                returnP95 = BigDecimal.ZERO,
            )
    }
}
