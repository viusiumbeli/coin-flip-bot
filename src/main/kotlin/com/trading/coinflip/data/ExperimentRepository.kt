package com.trading.coinflip.data

import com.trading.coinflip.model.BacktestRun
import com.trading.coinflip.model.Experiment
import com.trading.coinflip.model.ExperimentTrade
import com.trading.coinflip.model.Timeframe
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ExperimentRepository : JpaRepository<Experiment, Long> {

    fun findAllByOrderByCreatedAtDesc(): List<Experiment>

    fun findBySymbolAndTimeframeOrderByCreatedAtDesc(
        symbol: String,
        timeframe: Timeframe
    ): List<Experiment>

    @Query("SELECT e FROM Experiment e WHERE e.id IN :ids ORDER BY e.createdAt DESC")
    fun findByIdIn(ids: List<Long>): List<Experiment>
}

@Repository
interface BacktestRunRepository : JpaRepository<BacktestRun, Long> {

    fun findByExperimentIdOrderByRunNumberAsc(experimentId: Long): List<BacktestRun>

    fun findByExperimentIdOrderByRunNumberAsc(experimentId: Long, pageable: Pageable): Page<BacktestRun>

    @Query("SELECT COUNT(r) FROM BacktestRun r WHERE r.experiment.id = :experimentId")
    fun countByExperimentId(experimentId: Long): Long
}

@Repository
interface ExperimentTradeRepository : JpaRepository<ExperimentTrade, Long> {

    fun findByBacktestRunIdOrderByTradeNumberAsc(backtestRunId: Long): List<ExperimentTrade>

    @Query("SELECT COUNT(t) FROM ExperimentTrade t WHERE t.backtestRun.id = :backtestRunId")
    fun countByBacktestRunId(backtestRunId: Long): Long
}
