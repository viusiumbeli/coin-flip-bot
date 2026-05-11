package com.trading.coinflip.experiment

import com.trading.coinflip.backtest.model.BacktestRunDetailDto
import com.trading.coinflip.backtest.model.BacktestRunEntity
import com.trading.coinflip.backtest.model.BacktestRunSummaryDto

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

fun BacktestRunEntity.toDetailDto() =
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
    )
