package com.trading.coinflip.data

import com.trading.coinflip.common.model.ExperimentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ExperimentRepository : JpaRepository<ExperimentEntity, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<ExperimentEntity>

    @Query("SELECT e FROM ExperimentEntity e WHERE e.id IN :ids ORDER BY e.createdAt DESC")
    fun findByIdIn(ids: List<Long>): List<ExperimentEntity>
}
