package com.trading.coinflip.experiment

import com.trading.coinflip.common.model.ExperimentTradeEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ExperimentTradeRepository : CoroutineCrudRepository<ExperimentTradeEntity, Long> {
    fun findByBacktestRunIdOrderByTradeNumberAsc(backtestRunId: Long): Flow<ExperimentTradeEntity>

    @Query(
        """
        DELETE FROM experiment_trades
        WHERE backtest_run_id IN (SELECT id FROM backtest_runs WHERE experiment_id = :experimentId)
        """,
    )
    suspend fun deleteByExperimentId(experimentId: Long)
}
