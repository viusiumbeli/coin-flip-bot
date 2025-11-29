package com.trading.coinflip.api.experiment

import com.trading.coinflip.common.model.ExperimentStatus

data class CreateExperimentResponse(
    val id: Long,
    val status: ExperimentStatus,
    val message: String,
)
