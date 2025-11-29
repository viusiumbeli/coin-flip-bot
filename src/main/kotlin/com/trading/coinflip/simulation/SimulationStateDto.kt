package com.trading.coinflip.simulation

import com.trading.coinflip.common.dto.TradeDto

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
    val closedTrades: List<TradeDto>,
)
