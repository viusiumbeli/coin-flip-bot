package com.trading.coinflip.engine.model

import java.math.BigDecimal
import java.time.Duration

/**
 * Running statistics for trade metrics, updated incrementally during trade execution.
 * Eliminates need to store full trade list for experiments.
 *
 * Uses Welford's online algorithm for numerically stable variance calculation (for Sharpe ratio).
 */
class RunningTradeStats {
    var tradeCount: Int = 0
        private set
    var winCount: Int = 0
        private set
    var lossCount: Int = 0
        private set
    var totalWins: BigDecimal = BigDecimal.ZERO
        private set
    var totalLosses: BigDecimal = BigDecimal.ZERO
        private set
    var largestWin: BigDecimal = BigDecimal.ZERO
        private set
    var largestLoss: BigDecimal = BigDecimal.ZERO
        private set
    var totalDuration: Long = 0
        private set

    // Welford's algorithm for running mean and variance (for Sharpe ratio)
    var returnMean: Double = 0.0
        private set
    var returnM2: Double = 0.0
        private set

    /**
     * Update statistics with a completed trade.
     * Called during trade close event processing.
     */
    fun addTrade(trade: Trade) {
        tradeCount++
        val duration = Duration.between(trade.entryTime, trade.exitTime).toMinutes()
        totalDuration += duration

        when {
            trade.profitLoss > BigDecimal.ZERO -> {
                winCount++
                totalWins += trade.profitLoss
                if (trade.profitLoss > largestWin) largestWin = trade.profitLoss
            }
            trade.profitLoss < BigDecimal.ZERO -> {
                lossCount++
                totalLosses += trade.profitLoss.abs()
                if (trade.profitLoss < largestLoss) largestLoss = trade.profitLoss
            }
        }

        // Welford's online algorithm for running mean and variance
        val returnPercent = trade.profitLossPercent.toDouble()
        val delta = returnPercent - returnMean
        returnMean += delta / tradeCount
        val delta2 = returnPercent - returnMean
        returnM2 += delta * delta2
    }

    /**
     * Get variance from Welford's M2 value.
     * Returns 0.0 if fewer than 2 trades.
     */
    fun getVariance(): Double = if (tradeCount < 2) 0.0 else returnM2 / tradeCount
}
