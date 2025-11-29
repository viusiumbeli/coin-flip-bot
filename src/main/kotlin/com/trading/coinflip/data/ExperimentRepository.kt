package com.trading.coinflip.data

import com.trading.coinflip.common.model.BacktestRunEntity
import com.trading.coinflip.common.model.ExperimentEntity
import com.trading.coinflip.common.model.ExperimentTradeEntity
import com.trading.coinflip.common.model.Timeframe
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ExperimentRepository : JpaRepository<ExperimentEntity, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<ExperimentEntity>

    fun findBySymbolAndTimeframeOrderByCreatedAtDesc(
        symbol: String,
        timeframe: Timeframe,
    ): List<ExperimentEntity>

    @Query("SELECT e FROM ExperimentEntity e WHERE e.id IN :ids ORDER BY e.createdAt DESC")
    fun findByIdIn(ids: List<Long>): List<ExperimentEntity>
}

@Repository
interface BacktestRunRepository : JpaRepository<BacktestRunEntity, Long> {
    fun findByExperimentIdOrderByRunNumberAsc(experimentId: Long): List<BacktestRunEntity>

    fun findByExperimentIdOrderByRunNumberAsc(
        experimentId: Long,
        pageable: Pageable,
    ): Page<BacktestRunEntity>

    fun findByExperimentId(
        experimentId: Long,
        pageable: Pageable,
    ): Page<BacktestRunEntity>

    @Query("SELECT COUNT(r) FROM BacktestRunEntity r WHERE r.experiment.id = :experimentId")
    fun countByExperimentId(experimentId: Long): Long
}

@Repository
interface ExperimentTradeRepository : JpaRepository<ExperimentTradeEntity, Long> {
    fun findByBacktestRunIdOrderByTradeNumberAsc(backtestRunId: Long): List<ExperimentTradeEntity>

    @Query("SELECT COUNT(t) FROM ExperimentTradeEntity t WHERE t.backtestRun.id = :backtestRunId")
    fun countByBacktestRunId(backtestRunId: Long): Long
}
