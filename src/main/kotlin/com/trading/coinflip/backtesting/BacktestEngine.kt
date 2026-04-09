package com.trading.coinflip.backtesting

import com.trading.coinflip.analytics.PerformanceAnalytics
import com.trading.coinflip.model.BacktestConfig
import com.trading.coinflip.model.BacktestResult
import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.Position
import com.trading.coinflip.model.PositionSide
import com.trading.coinflip.model.PositionStatus
import com.trading.coinflip.model.Trade
import com.trading.coinflip.strategy.CoinFlipStrategy
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class BacktestEngine(
    private val strategy: CoinFlipStrategy,
    private val analytics: PerformanceAnalytics,
) {
    private val log = KotlinLogging.logger {}

    fun runBacktest(
        config: BacktestConfig,
        candles: List<Candle>,
    ): BacktestResult {
        log.debug { "Starting backtest for ${config.symbol} ${config.timeframe.label}" }
        log.debug { "Initial capital: ${config.initialCapital}, Risk per trade: ${config.riskPerTrade}%" }

        strategy.reset()

        var accountBalance = config.initialCapital
        var peakBalance = accountBalance
        var maxDrawdown = BigDecimal.ZERO

        val openPositions = mutableListOf<Position>()
        val closedTrades = mutableListOf<Trade>()
        var tradeIdCounter = 0L

        // Filter candles by date range if specified
        val backtestCandles =
            candles.filter { candle ->
                val afterStart = config.startDate?.let { candle.openTime >= it } ?: true
                val beforeEnd = config.endDate?.let { candle.openTime <= it } ?: true
                afterStart && beforeEnd
            }

        if (backtestCandles.isEmpty()) {
            log.warn { "No candles available for backtesting" }
            return createEmptyResult(config, candles)
        }

        log.debug {
            "Backtesting ${backtestCandles.size} candles from ${backtestCandles.first().openTime} to ${backtestCandles.last().openTime}"
        }

        // Walk through each candle
        for (i in backtestCandles.indices) {
            val candle = backtestCandles[i]

            // Update existing positions
            val positionsToClose = mutableListOf<Position>()
            for (position in openPositions) {
                val shouldClose =
                    strategy.updatePosition(
                        position = position,
                        candle = candle,
                        candles = backtestCandles,
                        candleIndex = i,
                        atrMultiplier = config.atrMultiplier,
                    )

                if (shouldClose) {
                    positionsToClose.add(position)
                }
            }

            // Close positions and update balance
            for (position in positionsToClose) {
                openPositions.remove(position)
                position.status = PositionStatus.CLOSED

                // Calculate closing balances
                val balanceBeforeClose = accountBalance

                // Calculate P&L and transaction costs
                val pnl =
                    when (position.side) {
                        PositionSide.LONG -> (position.exitPrice!! - position.entryPrice) * position.positionSize
                        PositionSide.SHORT -> (position.entryPrice - position.exitPrice!!) * position.positionSize
                    }
                val transactionCost =
                    position.entryPrice * position.positionSize *
                        (config.transactionCostPercent / BigDecimal(100)) * BigDecimal(2) // Entry + Exit

                accountBalance += pnl - transactionCost
                val balanceAfterClose = accountBalance

                val trade = position.toTrade(++tradeIdCounter, balanceBeforeClose, balanceAfterClose)
                closedTrades.add(trade)

                // Track drawdown
                if (accountBalance > peakBalance) {
                    peakBalance = accountBalance
                }
                val currentDrawdown = peakBalance - accountBalance
                if (currentDrawdown > maxDrawdown) {
                    maxDrawdown = currentDrawdown
                }

                log.debug {
                    "Closed ${trade.side} trade: P/L ${trade.profitLoss} (${trade.profitLossPercent}%), " +
                        "Balance: $accountBalance"
                }
            }

            // Consider opening new position if we have capacity
            if (openPositions.size < config.maxConcurrentPositions &&
                candle.atr != null &&
                accountBalance > BigDecimal.ZERO
            ) {
                // Calculate capital already allocated to open positions
                val allocatedCapital = openPositions.sumOf { it.allocatedCapital }
                val availableBalance = accountBalance - allocatedCapital

                // Only try to open if we have available capital
                if (availableBalance > BigDecimal.ZERO) {
                    // Random entry frequency based on config
                    if (kotlin.random.Random.nextDouble() < config.entryFrequency) {
                        val newPosition =
                            strategy.createPosition(
                                candle = candle,
                                candles = backtestCandles,
                                candleIndex = i,
                                accountBalance = availableBalance, // Use available balance for position sizing
                                riskPercent = config.riskPerTrade,
                                atrMultiplier = config.atrMultiplier,
                                balanceBeforeOpen = availableBalance, // Use available balance for reporting
                            )

                        if (newPosition != null) {
                            openPositions.add(newPosition)
                        }
                    }
                }
            }
        }

        // Close any remaining open positions at the last candle price
        val lastCandle = backtestCandles.last()
        for (position in openPositions) {
            position.exitPrice = lastCandle.close
            position.exitTime = lastCandle.openTime
            position.exitReason = "End of backtest period"
            position.status = PositionStatus.CLOSED

            // Calculate closing balances
            val balanceBeforeClose = accountBalance

            // Calculate P&L and transaction costs
            val pnl =
                when (position.side) {
                    PositionSide.LONG -> (position.exitPrice!! - position.entryPrice) * position.positionSize
                    PositionSide.SHORT -> (position.entryPrice - position.exitPrice!!) * position.positionSize
                }
            val transactionCost =
                position.entryPrice * position.positionSize *
                    (config.transactionCostPercent / BigDecimal(100)) * BigDecimal(2)

            accountBalance += pnl - transactionCost
            val balanceAfterClose = accountBalance

            val trade = position.toTrade(++tradeIdCounter, balanceBeforeClose, balanceAfterClose)
            closedTrades.add(trade)
        }

        log.debug { "Backtest completed. Closed ${closedTrades.size} trades" }
        log.debug {
            "Final balance: $accountBalance (${(
                (accountBalance - config.initialCapital) / config.initialCapital *
                    BigDecimal(
                        100,
                    )
            ).setScale(2, RoundingMode.HALF_UP)}%)"
        }

        // Calculate buy and hold performance
        val buyAndHoldReturn = calculateBuyAndHoldReturn(backtestCandles, config.initialCapital)

        return analytics.calculatePerformance(
            config = config,
            trades = closedTrades,
            finalCapital = accountBalance,
            maxDrawdown = maxDrawdown,
            peakBalance = peakBalance,
            buyAndHoldReturn = buyAndHoldReturn,
            startDate = backtestCandles.first().openTime,
            endDate = backtestCandles.last().openTime,
        )
    }

    private fun calculateBuyAndHoldReturn(
        candles: List<Candle>,
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
        candles: List<Candle>,
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
