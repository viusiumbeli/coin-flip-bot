package com.trading.coinflip.live.model

import java.math.BigDecimal
import java.time.Instant

data class LiveSnapshotDto(
    val id: Long,
    val balance: BigDecimal,
    val openPositionsCount: Int,
    val unrealizedPnl: BigDecimal,
    val candleTime: Instant,
)
