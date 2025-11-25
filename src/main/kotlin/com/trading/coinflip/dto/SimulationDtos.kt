package com.trading.coinflip.dto

import com.trading.coinflip.model.PositionSide
import java.math.BigDecimal
import java.time.Instant

data class SimulationInitRequest(
    val symbol: String,
    val timeframe: String,
    val startDate: String? = null,
    val endDate: String? = null
)

data class CandleDto(
    val openTime: Instant,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val atr: BigDecimal?
)

data class OpenPositionDto(
    val id: Long,
    val symbol: String,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val positionSize: BigDecimal,
    val initialStopLoss: BigDecimal,
    val trailingStop: BigDecimal,
    val unrealizedPnL: BigDecimal,
    val unrealizedPnLPercent: BigDecimal,
    val allocatedCapital: BigDecimal
)

data class SimulationMetricsDto(
    val accountBalance: BigDecimal,
    val peakBalance: BigDecimal,
    val drawdown: BigDecimal,
    val drawdownPercent: BigDecimal,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: BigDecimal,
    val openPositions: Int,
    val allocatedCapital: BigDecimal,
    val availableCapital: BigDecimal
)

data class SimulationStateDto(
    val initialized: Boolean,
    val symbol: String?,
    val timeframe: String?,
    val currentCandleIndex: Int,
    val totalCandles: Int,
    val currentCandle: CandleDto?,
    val previousCandle: CandleDto?,
    val metrics: SimulationMetricsDto,
    val openPositions: List<OpenPositionDto>,
    val closedTrades: List<TradeDto>
)
