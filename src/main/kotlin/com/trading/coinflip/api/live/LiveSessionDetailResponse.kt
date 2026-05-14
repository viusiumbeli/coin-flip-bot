package com.trading.coinflip.api.live

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.Exchange
import com.trading.coinflip.live.model.LiveSessionStatus
import java.math.BigDecimal
import java.time.Instant

data class LiveSessionDetailResponse(
    val id: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val exchange: Exchange,
    val status: LiveSessionStatus,
    val initialCapital: BigDecimal,
    val currentBalance: BigDecimal,
    val peakBalance: BigDecimal,
    val profitLoss: BigDecimal,
    val profitLossPercent: BigDecimal,
    val maxDrawdown: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val lastAtr: BigDecimal?,
    val lastCandleClose: BigDecimal?,
    val lastCandleTime: Instant?,
    val startedAt: Instant,
    val lastUpdateAt: Instant,
    val stoppedAt: Instant?,
    val errorMessage: String?,
    val reconnectCount: Int,
    val openPositions: List<LivePositionResponse>,
    val openPositionsCount: Int,
    val totalTradesCount: Long,
)
