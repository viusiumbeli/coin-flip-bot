package com.trading.coinflip.analytics

import com.trading.coinflip.model.BacktestConfig
import com.trading.coinflip.model.BacktestResult
import com.trading.coinflip.model.Trade
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import kotlin.math.sqrt

private val log = KotlinLogging.logger {}

@Component
class PerformanceAnalytics {

    fun calculatePerformance(
        config: BacktestConfig,
        trades: List<Trade>,
        finalCapital: BigDecimal,
        maxDrawdown: BigDecimal,
        buyAndHoldReturn: BigDecimal,
        startDate: Instant,
        endDate: Instant
    ): BacktestResult {
        if (trades.isEmpty()) {
            return createEmptyResult(
                config, finalCapital, buyAndHoldReturn, startDate, endDate
            )
        }

        val totalReturn = finalCapital - config.initialCapital
        val totalReturnPercent = (totalReturn / config.initialCapital * BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)

        val maxDrawdownPercent = (maxDrawdown / config.initialCapital * BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)

        val winningTrades = trades.filter { it.profitLoss > BigDecimal.ZERO }
        val losingTrades = trades.filter { it.profitLoss < BigDecimal.ZERO }

        // Debug: log some sample trades to understand the issue
        if (trades.isNotEmpty()) {
            log.info { "Debug: Total trades: ${trades.size}, Winning: ${winningTrades.size}, Losing: ${losingTrades.size}" }
            log.info { "Debug: First 5 trades P/L: ${trades.take(5).map { it.profitLoss }}" }
            log.info { "Debug: Sample winning trades: ${winningTrades.take(3).map { "P/L: ${it.profitLoss}, Entry: ${it.entryPrice}, Exit: ${it.exitPrice}, Size: ${it.positionSize}" }}" }
            log.info { "Debug: Sample losing trades: ${losingTrades.take(3).map { "P/L: ${it.profitLoss}, Entry: ${it.entryPrice}, Exit: ${it.exitPrice}, Size: ${it.positionSize}" }}" }
        }

        val winRate = if (trades.isNotEmpty()) {
            (BigDecimal(winningTrades.size) * BigDecimal(100) / BigDecimal(trades.size))
                .setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        log.info { "Debug: Win rate calculation: ${winningTrades.size} * 100 / ${trades.size} = $winRate" }

        val totalWins = winningTrades.sumOf { it.profitLoss }
        val totalLosses = losingTrades.sumOf { it.profitLoss }.abs()

        val profitFactor = if (totalLosses > BigDecimal.ZERO) {
            (totalWins / totalLosses).setScale(2, RoundingMode.HALF_UP)
        } else if (totalWins > BigDecimal.ZERO) {
            BigDecimal(999.99) // Arbitrarily large if no losses
        } else {
            BigDecimal.ZERO
        }

        val averageWin = if (winningTrades.isNotEmpty()) {
            (totalWins / BigDecimal(winningTrades.size)).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val averageLoss = if (losingTrades.isNotEmpty()) {
            (totalLosses / BigDecimal(losingTrades.size)).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val largestWin = winningTrades.maxOfOrNull { it.profitLoss } ?: BigDecimal.ZERO
        val largestLoss = losingTrades.minOfOrNull { it.profitLoss } ?: BigDecimal.ZERO

        val averageTradeDuration = if (trades.isNotEmpty()) {
            trades.map { Duration.between(it.entryTime, it.exitTime).toMinutes() }
                .average()
                .toLong()
        } else {
            0L
        }

        val sharpeRatio = calculateSharpeRatio(trades, startDate, endDate)

        val buyAndHoldReturnPercent = (buyAndHoldReturn / config.initialCapital * BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)

        return BacktestResult(
            config = config,
            trades = trades,
            initialCapital = config.initialCapital,
            finalCapital = finalCapital,
            totalReturn = totalReturn,
            totalReturnPercent = totalReturnPercent,
            maxDrawdown = maxDrawdown,
            maxDrawdownPercent = maxDrawdownPercent,
            winRate = winRate,
            profitFactor = profitFactor,
            sharpeRatio = sharpeRatio,
            totalTrades = trades.size,
            winningTrades = winningTrades.size,
            losingTrades = losingTrades.size,
            averageWin = averageWin,
            averageLoss = averageLoss,
            largestWin = largestWin,
            largestLoss = largestLoss,
            averageTradeDuration = averageTradeDuration,
            startDate = startDate,
            endDate = endDate,
            buyAndHoldReturn = buyAndHoldReturn,
            buyAndHoldReturnPercent = buyAndHoldReturnPercent
        )
    }

    private fun calculateSharpeRatio(
        trades: List<Trade>,
        startDate: Instant,
        endDate: Instant
    ): BigDecimal {
        if (trades.isEmpty()) return BigDecimal.ZERO

        // Calculate returns per trade
        val returns = trades.map { it.profitLossPercent.toDouble() }

        if (returns.isEmpty()) return BigDecimal.ZERO

        val meanReturn = returns.average()
        val variance = returns.map { (it - meanReturn) * (it - meanReturn) }.average()
        val stdDev = sqrt(variance)

        if (stdDev == 0.0) return BigDecimal.ZERO

        // Annualize assuming risk-free rate = 0 for simplicity
        val daysInPeriod = Duration.between(startDate, endDate).toDays()
        val annualizationFactor = if (daysInPeriod > 0) {
            sqrt(365.0 / daysInPeriod * trades.size)
        } else {
            1.0
        }

        val sharpe = (meanReturn / stdDev) * annualizationFactor

        return BigDecimal(sharpe).setScale(2, RoundingMode.HALF_UP)
    }

    private fun createEmptyResult(
        config: BacktestConfig,
        finalCapital: BigDecimal,
        buyAndHoldReturn: BigDecimal,
        startDate: Instant,
        endDate: Instant
    ): BacktestResult {
        return BacktestResult(
            config = config,
            trades = emptyList(),
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
            buyAndHoldReturnPercent = (buyAndHoldReturn / config.initialCapital * BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP)
        )
    }
}
