package com.trading.coinflip.backtest.model

import com.trading.coinflip.engine.model.Trade
import java.math.BigDecimal
import java.time.Instant

data class BacktestResult(
    val config: BacktestConfig,
    val trades: List<Trade>,
    val initialCapital: BigDecimal,
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
    val averageTradeDuration: Long, // in minutes
    val startDate: Instant,
    val endDate: Instant,
    val buyAndHoldReturn: BigDecimal,
    val buyAndHoldReturnPercent: BigDecimal,
)
