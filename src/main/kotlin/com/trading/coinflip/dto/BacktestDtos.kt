package com.trading.coinflip.dto

import com.trading.coinflip.model.BacktestResult
import com.trading.coinflip.model.PositionSide
import com.trading.coinflip.model.Timeframe
import java.math.BigDecimal
import java.time.Instant

data class BacktestRequest(
    val symbol: String,
    val timeframe: String,
    val startDate: String? = null,
    val endDate: String? = null
)

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
    val trades: List<TradeDto>
)

data class TradeDto(
    val id: Long,
    val symbol: String,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val exitTime: Instant,
    val exitPrice: BigDecimal,
    val positionSize: BigDecimal,
    val profitLoss: BigDecimal,
    val profitLossPercent: BigDecimal,
    val exitReason: String,
    val balanceBeforeOpen: BigDecimal,
    val balanceAfterOpen: BigDecimal,
    val balanceBeforeClose: BigDecimal,
    val balanceAfterClose: BigDecimal
)

data class AvailableSymbolsResponse(
    val symbols: List<String>,
    val timeframes: List<String>
)

fun BacktestResult.toDto(): BacktestResponse {
    return BacktestResponse(
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
        trades = trades.map { trade ->
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
                balanceAfterClose = trade.balanceAfterClose
            )
        }
    )
}
