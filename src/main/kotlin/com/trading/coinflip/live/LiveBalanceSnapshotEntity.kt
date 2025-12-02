package com.trading.coinflip.live

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("live_balance_snapshots")
data class LiveBalanceSnapshotEntity(
    @Id
    val id: Long? = null,
    @Column("session_id")
    val sessionId: Long,
    val balance: BigDecimal,
    @Column("open_positions_count")
    val openPositionsCount: Int = 0,
    @Column("unrealized_pnl")
    val unrealizedPnl: BigDecimal = BigDecimal.ZERO,
    @Column("candle_time")
    val candleTime: Instant,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)
