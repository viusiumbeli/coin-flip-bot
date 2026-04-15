package com.trading.coinflip.experiment

import com.trading.coinflip.common.dto.BacktestRunDetailDto
import com.trading.coinflip.common.dto.BacktestRunSummaryDto
import com.trading.coinflip.common.model.BacktestRunEntity
import com.trading.coinflip.common.model.ExperimentEntity
import com.trading.coinflip.common.model.ExperimentTradeEntity

fun BacktestRunEntity.toSummaryDto() =
    BacktestRunSummaryDto(
        id = id!!,
        runNumber = runNumber,
        totalReturnPercent = totalReturnPercent,
        winRate = winRate,
        sharpeRatio = sharpeRatio,
        profitFactor = profitFactor,
        maxDrawdownPercent = maxDrawdownPercent,
        totalTrades = totalTrades,
    )

fun BacktestRunEntity.toDetailDto(trades: List<ExperimentTradeEntity>) =
    BacktestRunDetailDto(
        id = id!!,
        runNumber = runNumber,
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
        buyAndHoldReturn = buyAndHoldReturn,
        buyAndHoldReturnPercent = buyAndHoldReturnPercent,
        trades = trades.map { it.toDto() },
    )

fun ExperimentTradeEntity.toDto() =
    ExperimentTradeDto(
        id = id!!,
        tradeNumber = tradeNumber,
        symbol = symbol,
        side = side,
        entryTime = entryTime,
        entryPrice = entryPrice,
        exitTime = exitTime,
        exitPrice = exitPrice,
        positionSize = positionSize,
        profitLoss = profitLoss,
        profitLossPercent = profitLossPercent,
        exitReason = exitReason,
        balanceBeforeOpen = balanceBeforeOpen,
        balanceAfterOpen = balanceAfterOpen,
        balanceBeforeClose = balanceBeforeClose,
        balanceAfterClose = balanceAfterClose,
    )
