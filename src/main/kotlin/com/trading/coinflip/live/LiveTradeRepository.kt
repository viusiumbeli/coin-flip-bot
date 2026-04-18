package com.trading.coinflip.live

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LiveTradeRepository : CoroutineCrudRepository<LiveTradeEntity, Long> {
    fun findBySessionIdOrderByExitTimeDesc(sessionId: Long): Flow<LiveTradeEntity>

    @Query("SELECT COUNT(*) FROM live_trades WHERE session_id = :sessionId")
    suspend fun countBySessionId(sessionId: Long): Long

    @Query(
        """
        SELECT * FROM live_trades
        WHERE session_id = :sessionId
        ORDER BY exit_time DESC
        LIMIT :limit
        """,
    )
    fun findRecentBySessionId(
        sessionId: Long,
        limit: Int,
    ): Flow<LiveTradeEntity>
}
