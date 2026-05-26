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

fun toBacktestResponse(result: BacktestResult): BacktestResponse =
    BacktestResponse(
        symbol = result.config.symbol,
        timeframe = result.config.timeframe.label,
        initialCapital = result.initialCapital,
        finalCapital = result.finalCapital,
        totalReturn = result.totalReturn,
        totalReturnPercent = result.totalReturnPercent,
        maxDrawdown = result.maxDrawdown,
        maxDrawdownPercent = result.maxDrawdownPercent,
        winRate = result.winRate,
        profitFactor = result.profitFactor,
        sharpeRatio = result.sharpeRatio,
        totalTrades = result.totalTrades,
        winningTrades = result.winningTrades,
        losingTrades = result.losingTrades,
        averageWin = result.averageWin,
        averageLoss = result.averageLoss,
        largestWin = result.largestWin,
        largestLoss = result.largestLoss,
        averageTradeDuration = result.averageTradeDuration,
        startDate = result.startDate,
        endDate = result.endDate,
        buyAndHoldReturn = result.buyAndHoldReturn,
        buyAndHoldReturnPercent = result.buyAndHoldReturnPercent,
        trades =
            result.trades.map { trade ->
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
                    transactionCost = trade.transactionCost,
                    exitReason = trade.exitReason,
                    balanceBeforeOpen = trade.balanceBeforeOpen,
                    balanceAfterOpen = trade.balanceAfterOpen,
                    balanceBeforeClose = trade.balanceBeforeClose,
                    balanceAfterClose = trade.balanceAfterClose,
                )
            },
    )
