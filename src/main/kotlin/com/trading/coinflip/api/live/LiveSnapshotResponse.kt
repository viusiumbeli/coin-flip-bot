package com.trading.coinflip.api.live

import java.math.BigDecimal
import java.time.Instant

data class LiveSnapshotResponse(
    val id: Long,
    val balance: BigDecimal,
    val openPositionsCount: Int,
    val unrealizedPnl: BigDecimal,
    val candleTime: Instant,
)
