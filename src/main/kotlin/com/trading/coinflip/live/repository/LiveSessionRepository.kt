package com.trading.coinflip.live.repository

import com.trading.coinflip.live.model.LiveSessionEntity
import com.trading.coinflip.live.model.LiveSessionStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LiveSessionRepository : CoroutineCrudRepository<LiveSessionEntity, Long> {

    fun findByStatus(status: LiveSessionStatus): Flow<LiveSessionEntity>

    fun findAllByOrderByStartedAtDesc(): Flow<LiveSessionEntity>
}
