package com.trading.coinflip.data

import com.trading.coinflip.common.model.BacktestRunEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BacktestRunRepository : JpaRepository<BacktestRunEntity, Long> {
    fun findByExperimentId(
        experimentId: Long,
        pageable: Pageable,
    ): Page<BacktestRunEntity>
}
