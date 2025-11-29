package com.trading.coinflip.api.experiment

import com.trading.coinflip.common.model.ExperimentStatus
import java.time.Instant

data class ExperimentStatusResponse(
    val id: Long,
    val status: ExperimentStatus,
    val totalRuns: Int,
    val completedRuns: Int,
    val failedRuns: Int,
    val progressPercent: Double,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val errorMessage: String?,
)