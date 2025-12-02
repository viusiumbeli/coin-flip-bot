package com.trading.coinflip.live

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LiveBalanceSnapshotRepository : CoroutineCrudRepository<LiveBalanceSnapshotEntity, Long> {
    fun findBySessionIdOrderByCandleTimeDesc(sessionId: Long): Flow<LiveBalanceSnapshotEntity>

    fun findBySessionIdOrderByCandleTimeAsc(sessionId: Long): Flow<LiveBalanceSnapshotEntity>

    @Query(
        """
        SELECT * FROM live_balance_snapshots
        WHERE session_id = :sessionId
        ORDER BY candle_time DESC
        LIMIT :limit
        """,
    )
    fun findRecentBySessionId(
        sessionId: Long,
        limit: Int,
    ): Flow<LiveBalanceSnapshotEntity>
}
