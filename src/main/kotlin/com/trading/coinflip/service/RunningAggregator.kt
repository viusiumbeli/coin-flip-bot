package com.trading.coinflip.service

import com.trading.coinflip.model.BacktestResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe running aggregator for computing average statistics across many backtest results.
 * Uses running sums to avoid storing all results in memory.
 */
class RunningAggregator {
    private val count = AtomicInteger(0)

    // Running sums for BigDecimal fields
    private var sumFinalCapital = BigDecimal.ZERO
    private var sumTotalReturn = BigDecimal.ZERO
    private var sumTotalReturnPercent = BigDecimal.ZERO
    private var sumMaxDrawdown = BigDecimal.ZERO
    private var sumMaxDrawdownPercent = BigDecimal.ZERO
    private var sumWinRate = BigDecimal.ZERO
    private var sumProfitFactor = BigDecimal.ZERO
    private var sumSharpeRatio = BigDecimal.ZERO
    private var sumAverageWin = BigDecimal.ZERO
    private var sumAverageLoss = BigDecimal.ZERO
    private var sumLargestWin = BigDecimal.ZERO
    private var sumLargestLoss = BigDecimal.ZERO

    // Running sums for Int/Long fields
    private var sumTotalTrades = 0L
    private var sumWinningTrades = 0L
    private var sumLosingTrades = 0L
    private var sumAverageTradeDuration = 0L

    // For buy & hold (same for all runs, just take from first)
    private var buyAndHoldReturn: BigDecimal? = null
    private var buyAndHoldReturnPercent: BigDecimal? = null

    @Synchronized
    fun add(result: BacktestResult) {
        count.incrementAndGet()

        sumFinalCapital += result.finalCapital
        sumTotalReturn += result.totalReturn
        sumTotalReturnPercent += result.totalReturnPercent
        sumMaxDrawdown += result.maxDrawdown
        sumMaxDrawdownPercent += result.maxDrawdownPercent
        sumWinRate += result.winRate
        sumProfitFactor += result.profitFactor
        sumSharpeRatio += result.sharpeRatio
        sumAverageWin += result.averageWin
        sumAverageLoss += result.averageLoss
        sumLargestWin += result.largestWin
        sumLargestLoss += result.largestLoss

        sumTotalTrades += result.totalTrades
        sumWinningTrades += result.winningTrades
        sumLosingTrades += result.losingTrades
        sumAverageTradeDuration += result.averageTradeDuration

        // Buy & hold is the same for all runs (same candle data)
        if (buyAndHoldReturn == null) {
            buyAndHoldReturn = result.buyAndHoldReturn
            buyAndHoldReturnPercent = result.buyAndHoldReturnPercent
        }
    }

    fun getCount(): Int = count.get()

    @Synchronized
    fun computeAverages(): AggregatedStats {
        val n = count.get()
        if (n == 0) {
            return AggregatedStats.empty()
        }

        val divisor = BigDecimal(n)

        return AggregatedStats(
            finalCapital = sumFinalCapital.divide(divisor, 8, RoundingMode.HALF_UP),
            totalReturn = sumTotalReturn.divide(divisor, 8, RoundingMode.HALF_UP),
            totalReturnPercent = sumTotalReturnPercent.divide(divisor, 8, RoundingMode.HALF_UP),
            maxDrawdown = sumMaxDrawdown.divide(divisor, 8, RoundingMode.HALF_UP),
            maxDrawdownPercent = sumMaxDrawdownPercent.divide(divisor, 8, RoundingMode.HALF_UP),
            winRate = sumWinRate.divide(divisor, 8, RoundingMode.HALF_UP),
            profitFactor = sumProfitFactor.divide(divisor, 8, RoundingMode.HALF_UP),
            sharpeRatio = sumSharpeRatio.divide(divisor, 8, RoundingMode.HALF_UP),
            totalTrades = (sumTotalTrades.toDouble() / n).toInt(),
            winningTrades = (sumWinningTrades.toDouble() / n).toInt(),
            losingTrades = (sumLosingTrades.toDouble() / n).toInt(),
            averageWin = sumAverageWin.divide(divisor, 8, RoundingMode.HALF_UP),
            averageLoss = sumAverageLoss.divide(divisor, 8, RoundingMode.HALF_UP),
            largestWin = sumLargestWin.divide(divisor, 8, RoundingMode.HALF_UP),
            largestLoss = sumLargestLoss.divide(divisor, 8, RoundingMode.HALF_UP),
            averageTradeDuration = (sumAverageTradeDuration.toDouble() / n).toLong(),
            buyAndHoldReturn = buyAndHoldReturn ?: BigDecimal.ZERO,
            buyAndHoldReturnPercent = buyAndHoldReturnPercent ?: BigDecimal.ZERO
        )
    }
}

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
    val buyAndHoldReturnPercent: BigDecimal
) {
    companion object {
        fun empty() = AggregatedStats(
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
            buyAndHoldReturnPercent = BigDecimal.ZERO
        )
    }
}
