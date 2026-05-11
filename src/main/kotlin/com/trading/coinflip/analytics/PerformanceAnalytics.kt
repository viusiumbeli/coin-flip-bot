package com.trading.coinflip.analytics

import com.trading.coinflip.backtest.model.BacktestConfig
import com.trading.coinflip.backtest.model.BacktestResult
import com.trading.coinflip.engine.model.RunningTradeStats
import com.trading.coinflip.engine.model.Trade
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import kotlin.math.sqrt

@Component
class PerformanceAnalytics {
    /**
     * Calculate performance from running statistics (no trade list required).
     * Used by experiments to avoid storing millions of trade objects.
     */
    fun calculatePerformance(
        config: BacktestConfig,
        stats: RunningTradeStats,
        finalCapital: BigDecimal,
        maxDrawdown: BigDecimal,
        peakBalance: BigDecimal,
        buyAndHoldReturn: BigDecimal,
        startDate: Instant,
        endDate: Instant,
        trades: List<Trade> = emptyList(),
    ): BacktestResult {
        if (stats.tradeCount == 0) {
            return createEmptyResult(
                config,
                finalCapital,
                buyAndHoldReturn,
                startDate,
                endDate,
            )
        }

        val totalReturn = finalCapital - config.initialCapital
        val totalReturnPercent =
            (totalReturn / config.initialCapital * BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP)

        val maxDrawdownPercent =
            if (peakBalance > BigDecimal.ZERO) {
                (maxDrawdown / peakBalance * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        val winRate =
            (BigDecimal(stats.winCount) * BigDecimal(100) / BigDecimal(stats.tradeCount))
                .setScale(2, RoundingMode.HALF_UP)

        val profitFactor =
            if (stats.totalLosses > BigDecimal.ZERO) {
                stats.totalWins.divide(stats.totalLosses, 2, RoundingMode.HALF_UP)
            } else if (stats.totalWins > BigDecimal.ZERO) {
                BigDecimal("999.99")
            } else {
                BigDecimal.ZERO
            }

        val averageWin =
            if (stats.winCount > 0) {
                stats.totalWins.divide(BigDecimal(stats.winCount), 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        val averageLoss =
            if (stats.lossCount > 0) {
                stats.totalLosses.divide(BigDecimal(stats.lossCount), 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        val averageTradeDuration =
            if (stats.tradeCount > 0) {
                stats.totalDuration / stats.tradeCount
            } else {
                0L
            }

        val sharpeRatio = calculateSharpe(stats, startDate, endDate)

        val buyAndHoldReturnPercent =
            (buyAndHoldReturn / config.initialCapital * BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP)

        return BacktestResult(
            config = config,
            initialCapital = config.initialCapital,
            finalCapital = finalCapital,
            totalReturn = totalReturn,
            totalReturnPercent = totalReturnPercent,
            maxDrawdown = maxDrawdown,
            maxDrawdownPercent = maxDrawdownPercent,
            winRate = winRate,
            profitFactor = profitFactor,
            sharpeRatio = sharpeRatio,
            totalTrades = stats.tradeCount,
            winningTrades = stats.winCount,
            losingTrades = stats.lossCount,
            averageWin = averageWin,
            averageLoss = averageLoss,
            largestWin = stats.largestWin,
            largestLoss = stats.largestLoss,
            averageTradeDuration = averageTradeDuration,
            startDate = startDate,
            endDate = endDate,
            buyAndHoldReturn = buyAndHoldReturn,
            buyAndHoldReturnPercent = buyAndHoldReturnPercent,
            trades = trades,
        )
    }

    /**
     * Calculate Sharpe ratio from running statistics using Welford's algorithm values.
     */
    private fun calculateSharpe(
        stats: RunningTradeStats,
        startDate: Instant,
        endDate: Instant,
    ): BigDecimal {
        if (stats.tradeCount < 2) return BigDecimal.ZERO

        val variance = stats.getVariance()
        val stdDev = sqrt(variance)

        if (stdDev == 0.0) return BigDecimal.ZERO

        val daysInPeriod = Duration.between(startDate, endDate).toDays()
        val annualizationFactor =
            if (daysInPeriod > 0) {
                sqrt(365.0 / daysInPeriod * stats.tradeCount)
            } else {
                1.0
            }

        val sharpe = (stats.returnMean / stdDev) * annualizationFactor

        return BigDecimal(sharpe).setScale(2, RoundingMode.HALF_UP)
    }

    private fun createEmptyResult(
        config: BacktestConfig,
        finalCapital: BigDecimal,
        buyAndHoldReturn: BigDecimal,
        startDate: Instant,
        endDate: Instant,
    ): BacktestResult =
        BacktestResult(
            config = config,
            initialCapital = config.initialCapital,
            finalCapital = finalCapital,
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
            startDate = startDate,
            endDate = endDate,
            buyAndHoldReturn = buyAndHoldReturn,
            buyAndHoldReturnPercent =
                (buyAndHoldReturn / config.initialCapital * BigDecimal(100))
                    .setScale(2, RoundingMode.HALF_UP),
        )
}
