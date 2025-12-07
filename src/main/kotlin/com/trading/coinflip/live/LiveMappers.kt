package com.trading.coinflip.live

import com.trading.coinflip.api.live.LivePositionResponse
import com.trading.coinflip.api.live.LiveSessionDetailResponse
import com.trading.coinflip.api.live.LiveSessionSummaryResponse
import com.trading.coinflip.api.live.LiveSnapshotResponse
import com.trading.coinflip.api.live.LiveTradeResponse
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.live.model.LiveBalanceSnapshotEntity
import com.trading.coinflip.live.model.LivePositionEntity
import com.trading.coinflip.live.model.LiveSessionEntity
import com.trading.coinflip.live.model.LiveTradeEntity
import java.math.BigDecimal
import java.math.RoundingMode

fun LiveSessionEntity.toSummaryDto(): LiveSessionSummaryResponse {
    val pnl = currentBalance - initialCapital
    val pnlPercent =
        if (initialCapital > BigDecimal.ZERO) {
            pnl.divide(initialCapital, 4, RoundingMode.HALF_UP) * BigDecimal(100)
        } else {
            BigDecimal.ZERO
        }
    return LiveSessionSummaryResponse(
        id = id!!,
        symbol = symbol,
        timeframe = timeframe,
        exchange = exchange,
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
    positions: List<LivePositionResponse>,
    tradesCount: Long,
    lastCandle: CandleEntity?,
): LiveSessionDetailResponse {
    val pnl = currentBalance - initialCapital
    val pnlPercent =
        if (initialCapital > BigDecimal.ZERO) {
            pnl.divide(initialCapital, 4, RoundingMode.HALF_UP) * BigDecimal(100)
        } else {
            BigDecimal.ZERO
        }
    val drawdownPercent =
        if (peakBalance > BigDecimal.ZERO) {
            maxDrawdown.divide(peakBalance, 4, RoundingMode.HALF_UP) * BigDecimal(100)
        } else {
            BigDecimal.ZERO
        }
    return LiveSessionDetailResponse(
        id = id!!,
        symbol = symbol,
        timeframe = timeframe,
        exchange = exchange,
        status = status,
        initialCapital = initialCapital,
        currentBalance = currentBalance,
        peakBalance = peakBalance,
        profitLoss = pnl,
        profitLossPercent = pnlPercent,
        maxDrawdown = maxDrawdown,
        maxDrawdownPercent = drawdownPercent,
        lastAtr = lastCandle?.atr,
        lastCandleClose = lastCandle?.close,
        lastCandleTime = lastCandle?.openTime,
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

fun LivePositionEntity.toDto(currentPrice: BigDecimal?): LivePositionResponse {
    val unrealizedPnl =
        currentPrice?.let { price ->
            when (side) {
                PositionSide.LONG -> (price - entryPrice) * positionSize
                PositionSide.SHORT -> (entryPrice - price) * positionSize
            }
        }
    val unrealizedPnlPercent =
        unrealizedPnl?.let { pnl ->
            if (allocatedCapital > BigDecimal.ZERO) {
                pnl.divide(allocatedCapital, 4, RoundingMode.HALF_UP) * BigDecimal(100)
            } else {
                BigDecimal.ZERO
            }
        }
    return LivePositionResponse(
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
    LiveTradeResponse(
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
    LiveSnapshotResponse(
        id = id!!,
        balance = balance,
        openPositionsCount = openPositionsCount,
        unrealizedPnl = unrealizedPnl,
        candleTime = candleTime,
    )
