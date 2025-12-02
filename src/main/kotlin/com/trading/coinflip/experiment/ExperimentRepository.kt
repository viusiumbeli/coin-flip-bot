package com.trading.coinflip.experiment

import com.trading.coinflip.experiment.model.ExperimentEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ExperimentRepository : CoroutineCrudRepository<ExperimentEntity, Long> {
    fun findAllByOrderByCreatedAtDesc(): Flow<ExperimentEntity>

    @Query("SELECT * FROM experiments WHERE id = ANY(:ids) ORDER BY created_at DESC")
    fun findByIdIn(ids: List<Long>): Flow<ExperimentEntity>
}