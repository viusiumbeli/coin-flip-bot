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

@Component
class BacktestEngine(
    private val processor: TradingProcessor,
    private val analytics: PerformanceAnalytics,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Run a backtest and return the result.
     *
     * The engine is stateless - caller must provide a fresh MutableTradingState.
     * Trade collection is controlled by the state's collectTrades flag.
     */
    fun runBacktest(
        state: MutableTradingState,
        config: BacktestConfig,
        candles: List<CandleEntity>,
    ): BacktestResult {
        if (candles.isEmpty()) {
            log.warn { "No candles available for backtesting" }
            return createEmptyResult(config)
        }

        processCandles(state, candles)
        closeOpenPositions(state, candles.last())
        return generateResult(config, state, candles)
    }

    /**
     * Process all candles through the trading processor.
     */
    fun processCandles(
        state: MutableTradingState,
        candles: List<CandleEntity>,
    ) {
        for (candle in candles) {
            val events = processor.processCandle(state, candle)
            state.applyEvents(events)
        }
    }

    /**
     * Close any remaining open positions at the last candle price.
     */
    fun closeOpenPositions(
        state: MutableTradingState,
        lastCandle: CandleEntity,
    ) {
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
    }

    /**
     * Generate performance result from trading state.
     */
    fun generateResult(
        config: BacktestConfig,
        state: MutableTradingState,
        candles: List<CandleEntity>,
    ): BacktestResult = analytics.calculatePerformance(config, state, candles)

    internal fun createEmptyResult(config: BacktestConfig): BacktestResult {
        val startDate = config.startDate ?: java.time.Instant.now()
        val endDate = config.endDate ?: java.time.Instant.now()

        return BacktestResult(
            config = config,
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
