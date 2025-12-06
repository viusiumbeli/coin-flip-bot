package com.trading.coinflip.backtest

import com.trading.coinflip.analytics.PerformanceAnalytics
import com.trading.coinflip.backtest.model.BacktestConfig
import com.trading.coinflip.backtest.model.BacktestResult
import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.TradingProcessor
import com.trading.coinflip.engine.model.MutableTradingState
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class BacktestEngine(
    private val processor: TradingProcessor,
    private val analytics: PerformanceAnalytics,
) {
    private val log = KotlinLogging.logger {}

    fun runBacktest(
        config: BacktestConfig,
        candles: List<CandleEntity>,
    ): BacktestResult {
        log.info { "Starting backtest for ${config.symbol} ${config.timeframe.label}" }

        // Use MutableTradingState for high-performance backtest execution
        val state = MutableTradingState.create(config.initialCapital)

        if (candles.isEmpty()) {
            log.warn { "No candles available for backtesting" }
            return createEmptyResult(config, candles)
        }

        log.info {
            "Backtesting ${candles.size} candles from ${candles.first().openTime} to ${candles.last().openTime}"
        }

        // Single source of truth - TradingProcessor handles ALL trading logic
        for (candle in candles) {
            val events = processor.processCandle(state, candle)
            state.applyEvents(events)
        }

        // Close any remaining open positions at the last candle price
        val lastCandle = candles.last()
        for (position in state.openPositions.toList()) {
            val closeEvent =
                processor.forceClosePosition(
                    state = state,
                    position = position,
                    exitPrice = lastCandle.close,
                    exitTime = lastCandle.openTime,
                    exitReason = "End of backtest period",
                )
            state.applyEvent(closeEvent)
        }

        // Calculate buy and hold performance
        val buyAndHoldReturn = calculateBuyAndHoldReturn(candles, config.initialCapital)

        return analytics.calculatePerformance(
            config = config,
            trades = state.closedTrades,
            finalCapital = state.accountBalance,
            maxDrawdown = state.maxDrawdown,
            peakBalance = state.peakBalance,
            buyAndHoldReturn = buyAndHoldReturn,
            startDate = candles.first().openTime,
            endDate = candles.last().openTime,
        )
    }

    private fun calculateBuyAndHoldReturn(
        candles: List<CandleEntity>,
        initialCapital: BigDecimal,
    ): BigDecimal {
        if (candles.isEmpty()) return BigDecimal.ZERO

        val firstPrice = candles.first().close
        val lastPrice = candles.last().close
        val priceChange = lastPrice - firstPrice
        val percentChange = priceChange.divide(firstPrice, 8, RoundingMode.HALF_UP)

        return initialCapital * percentChange
    }

    private fun createEmptyResult(
        config: BacktestConfig,
        candles: List<CandleEntity>,
    ): BacktestResult {
        val startDate = candles.firstOrNull()?.openTime ?: config.startDate ?: java.time.Instant.now()
        val endDate = candles.lastOrNull()?.openTime ?: config.endDate ?: java.time.Instant.now()

        return BacktestResult(
            config = config,
            trades = emptyList(),
            initialCapital = config.initialCapital,
            finalCapital = config.initialCapital,
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
            buyAndHoldReturn = BigDecimal.ZERO,
            buyAndHoldReturnPercent = BigDecimal.ZERO,
        )
    }
}
