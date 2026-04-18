package com.trading.coinflip.backtest

import com.trading.coinflip.backtest.model.BacktestRunEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface BacktestRunRepository : CoroutineCrudRepository<BacktestRunEntity, Long> {
    // Manual pagination - R2DBC doesn't support Pageable
    @Query(
        """
        SELECT * FROM backtest_runs
        WHERE experiment_id = :experimentId
        ORDER BY run_number ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun findByExperimentIdPaginated(
        experimentId: Long,
        limit: Int,
        offset: Long,
    ): Flow<BacktestRunEntity>

    @Query("SELECT COUNT(*) FROM backtest_runs WHERE experiment_id = :experimentId")
    suspend fun countByExperimentId(experimentId: Long): Long

    // For non-paginated access
    fun findByExperimentId(experimentId: Long): Flow<BacktestRunEntity>

    // Manual cascade delete - R2DBC doesn't honor ON DELETE CASCADE
    @Query("DELETE FROM backtest_runs WHERE experiment_id = :experimentId")
    suspend fun deleteByExperimentId(experimentId: Long)
}
