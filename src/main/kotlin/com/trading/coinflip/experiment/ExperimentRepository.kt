package com.trading.coinflip.experiment

import com.trading.coinflip.experiment.model.ExperimentEntity
import com.trading.coinflip.experiment.model.ExperimentStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ExperimentRepository : CoroutineCrudRepository<ExperimentEntity, Long> {
    fun findAllByOrderByCreatedAtDesc(): Flow<ExperimentEntity>

    @Query("SELECT * FROM experiments WHERE id = ANY(:ids) ORDER BY created_at DESC")
    fun findByIdIn(ids: List<Long>): Flow<ExperimentEntity>

    @Modifying
    @Query("UPDATE experiments SET status = :status, started_at = NOW() WHERE id = :id")
    suspend fun markExperimentRunning(
        id: Long,
        status: ExperimentStatus = ExperimentStatus.RUNNING,
    )

    @Modifying
    @Query("UPDATE experiments SET status = :status, finished_at = NOW() WHERE id = :id")
    suspend fun markExperimentCancelled(
        id: Long,
        status: ExperimentStatus = ExperimentStatus.CANCELLED,
    )

    @Modifying
    @Query("UPDATE experiments SET status = :status, error_message = :errorMessage, finished_at = NOW() WHERE id = :id")
    suspend fun markExperimentFailed(
        id: Long,
        errorMessage: String,
        status: ExperimentStatus = ExperimentStatus.FAILED,
    )

    @Modifying
    @Query("UPDATE experiments SET failed_runs = failed_runs + 1 WHERE id = :id")
    suspend fun incrementFailedRuns(id: Long)

    @Modifying
    @Query("UPDATE experiments SET completed_runs = :completedRuns WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        completedRuns: Int,
    )

    @Modifying
    @Query("DELETE FROM experiments WHERE id = :id")
    suspend fun deleteExperimentById(id: Long): Int
}
