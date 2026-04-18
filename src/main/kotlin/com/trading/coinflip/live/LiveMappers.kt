package com.trading.coinflip.live

import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.live.model.LivePositionDto
import com.trading.coinflip.live.model.LiveSessionDetailDto
import com.trading.coinflip.live.model.LiveSessionSummaryDto
import com.trading.coinflip.live.model.LiveSnapshotDto
import com.trading.coinflip.live.model.LiveTradeDto
import java.math.BigDecimal
import java.math.RoundingMode

fun LiveSessionEntity.toSummaryDto(): LiveSessionSummaryDto {
    val pnl = currentBalance - initialCapital
    val pnlPercent = if (initialCapital > BigDecimal.ZERO) {
        pnl.divide(initialCapital, 4, RoundingMode.HALF_UP) * BigDecimal(100)
    } else {
        BigDecimal.ZERO
    }
    return LiveSessionSummaryDto(
        id = id!!,
        symbol = symbol,
        timeframe = timeframe,
        status = status,
        initialCapital = initialCapital,
        currentBalance = currentBalance,
        profitLoss = pnl,
        profitLossPercent = pnlPercent,
        maxDrawdown = maxDrawdown,
        startedAt = startedAt,
        lastUpdateAt = lastUpdateAt,
        stoppedAt = stoppedAt,
        errorMessage = errorMessage,
    )
}

fun LiveSessionEntity.toDetailDto(
    positions: List<LivePositionDto>,
    tradesCount: Long,
): LiveSessionDetailDto {
    val pnl = currentBalance - initialCapital
    val pnlPercent = if (initialCapital > BigDecimal.ZERO) {
        pnl.divide(initialCapital, 4, RoundingMode.HALF_UP) * BigDecimal(100)
    } else {
        BigDecimal.ZERO
    }
    val drawdownPercent = if (peakBalance > BigDecimal.ZERO) {
        maxDrawdown.divide(peakBalance, 4, RoundingMode.HALF_UP) * BigDecimal(100)
    } else {
        BigDecimal.ZERO
    }
    return LiveSessionDetailDto(
        id = id!!,
        symbol = symbol,
        timeframe = timeframe,
        status = status,
        initialCapital = initialCapital,
        currentBalance = currentBalance,
        peakBalance = peakBalance,
        profitLoss = pnl,
        profitLossPercent = pnlPercent,
        maxDrawdown = maxDrawdown,
        maxDrawdownPercent = drawdownPercent,
        lastAtr = lastAtr,
        lastCandleClose = lastCandleClose,
        lastCandleTime = lastCandleTime,
        startedAt = startedAt,
        lastUpdateAt = lastUpdateAt,
        stoppedAt = stoppedAt,
        errorMessage = errorMessage,
        reconnectCount = reconnectCount,
        openPositions = positions,
        openPositionsCount = positions.size,
        totalTradesCount = tradesCount,
    )
}

fun LivePositionEntity.toDto(currentPrice: BigDecimal?): LivePositionDto {
    val unrealizedPnl = currentPrice?.let { price ->
        when (side) {
            PositionSide.LONG -> (price - entryPrice) * positionSize
            PositionSide.SHORT -> (entryPrice - price) * positionSize
        }
    }
    val unrealizedPnlPercent = unrealizedPnl?.let { pnl ->
        if (allocatedCapital > BigDecimal.ZERO) {
            pnl.divide(allocatedCapital, 4, RoundingMode.HALF_UP) * BigDecimal(100)
        } else {
            BigDecimal.ZERO
        }
    }
    return LivePositionDto(
        id = id!!,
        positionId = positionId,
        symbol = symbol,
        timeframe = timeframe,
        side = side,
        entryTime = entryTime,
        entryPrice = entryPrice,
        positionSize = positionSize,
        initialStopLoss = initialStopLoss,
        trailingStop = trailingStop,
        highestFavorablePrice = highestFavorablePrice,
        allocatedCapital = allocatedCapital,
        unrealizedPnl = unrealizedPnl,
        unrealizedPnlPercent = unrealizedPnlPercent,
        currentPrice = currentPrice,
    )
}

fun LiveTradeEntity.toDto() =
    LiveTradeDto(
        id = id!!,
        tradeId = tradeId,
        symbol = symbol,
        timeframe = timeframe,
        side = side,
        entryTime = entryTime,
        entryPrice = entryPrice,
        exitTime = exitTime,
        exitPrice = exitPrice,
        positionSize = positionSize,
        profitLoss = profitLoss,
        profitLossPercent = profitLossPercent,
        exitReason = exitReason,
    )

fun LiveBalanceSnapshotEntity.toDto() =
    LiveSnapshotDto(
        id = id!!,
        balance = balance,
        openPositionsCount = openPositionsCount,
        unrealizedPnl = unrealizedPnl,
        candleTime = candleTime,
    )
