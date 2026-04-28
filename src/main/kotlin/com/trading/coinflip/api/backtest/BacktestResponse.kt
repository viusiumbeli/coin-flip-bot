package com.trading.coinflip.api.backtest

import com.trading.coinflip.backtest.model.BacktestResult
import com.trading.coinflip.common.dto.TradeDto
import java.math.BigDecimal
import java.time.Instant

data class BacktestResponse(
    val symbol: String,
    val timeframe: String,
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
    val averageTradeDuration: Long,
    val startDate: Instant,
    val endDate: Instant,
    val buyAndHoldReturn: BigDecimal,
    val buyAndHoldReturnPercent: BigDecimal,
    val trades: List<TradeDto>,
)

fun BacktestResult.toResponse(): BacktestResponse =
    BacktestResponse(
        symbol = config.symbol,
        timeframe = config.timeframe.label,
        initialCapital = initialCapital,
        finalCapital = finalCapital,
        totalReturn = totalReturn,
        totalReturnPercent = totalReturnPercent,
        maxDrawdown = maxDrawdown,
        maxDrawdownPercent = maxDrawdownPercent,
        winRate = winRate,
        profitFactor = profitFactor,
        sharpeRatio = sharpeRatio,
        totalTrades = totalTrades,
        winningTrades = winningTrades,
        losingTrades = losingTrades,
        averageWin = averageWin,
        averageLoss = averageLoss,
        largestWin = largestWin,
        largestLoss = largestLoss,
        averageTradeDuration = averageTradeDuration,
        startDate = startDate,
        endDate = endDate,
        buyAndHoldReturn = buyAndHoldReturn,
        buyAndHoldReturnPercent = buyAndHoldReturnPercent,
        trades =
            trades.map { trade ->
                TradeDto(
                    id = trade.id,
                    symbol = trade.symbol,
                    side = trade.side,
                    entryTime = trade.entryTime,
                    entryPrice = trade.entryPrice,
                    exitTime = trade.exitTime,
                    exitPrice = trade.exitPrice,
                    positionSize = trade.positionSize,
                    profitLoss = trade.profitLoss,
                    profitLossPercent = trade.profitLossPercent,
                    exitReason = trade.exitReason,
                    balanceBeforeOpen = trade.balanceBeforeOpen,
                    balanceAfterOpen = trade.balanceAfterOpen,
                    balanceBeforeClose = trade.balanceBeforeClose,
                    balanceAfterClose = trade.balanceAfterClose,
                )
            },
    )
