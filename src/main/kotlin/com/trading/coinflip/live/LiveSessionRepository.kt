package com.trading.coinflip.live

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LiveSessionRepository : CoroutineCrudRepository<LiveSessionEntity, Long> {
    suspend fun findBySymbolAndStatus(
        symbol: String,
        status: LiveSessionStatus,
    ): LiveSessionEntity?

    fun findByStatus(status: LiveSessionStatus): Flow<LiveSessionEntity>

    fun findAllByOrderByStartedAtDesc(): Flow<LiveSessionEntity>

    @Query("SELECT * FROM live_sessions WHERE symbol = :symbol ORDER BY started_at DESC LIMIT 1")
    suspend fun findLatestBySymbol(symbol: String): LiveSessionEntity?
}
