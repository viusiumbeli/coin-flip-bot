package com.trading.coinflip.api.live

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.live.model.LiveSessionStatus
import java.math.BigDecimal
import java.time.Instant

data class LiveSessionSummaryResponse(
    val id: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val status: LiveSessionStatus,
    val initialCapital: BigDecimal,
    val currentBalance: BigDecimal,
    val profitLoss: BigDecimal,
    val profitLossPercent: BigDecimal,
    val maxDrawdown: BigDecimal,
    val startedAt: Instant,
    val lastUpdateAt: Instant,
    val stoppedAt: Instant?,
    val errorMessage: String?,
)
