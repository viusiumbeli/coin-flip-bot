package com.trading.coinflip.simulation

import com.trading.coinflip.common.dto.TradeDto

data class SimulationStateDto(
    val initialized: Boolean = false,
    val symbol: String? = null,
    val timeframe: String? = null,
    val currentCandleIndex: Int = -1,
    val totalCandles: Int = 0,
    val currentCandle: CandleDto? = null,
    val previousCandle: CandleDto? = null,
    val metrics: SimulationMetricsDto = SimulationMetricsDto(),
    val openPositions: List<OpenPositionDto> = emptyList(),
    val closedTrades: List<TradeDto> = emptyList(),
)
