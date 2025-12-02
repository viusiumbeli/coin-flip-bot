package com.trading.coinflip.live

import com.trading.coinflip.engine.model.PositionStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LivePositionRepository : CoroutineCrudRepository<LivePositionEntity, Long> {
    fun findBySessionIdAndStatus(
        sessionId: Long,
        status: PositionStatus,
    ): Flow<LivePositionEntity>

    fun findBySessionId(sessionId: Long): Flow<LivePositionEntity>

    suspend fun findBySessionIdAndPositionId(
        sessionId: Long,
        positionId: Long,
    ): LivePositionEntity?

    suspend fun deleteBySessionIdAndPositionId(
        sessionId: Long,
        positionId: Long,
    )
}
