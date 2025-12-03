package com.trading.coinflip.live.repository

import com.trading.coinflip.live.model.LiveBalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LiveBalanceSnapshotRepository : CoroutineCrudRepository<LiveBalanceSnapshotEntity, Long> {
    fun findBySessionIdOrderByCandleTimeAsc(sessionId: Long): Flow<LiveBalanceSnapshotEntity>
}
