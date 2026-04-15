package com.trading.coinflip.api.experiment

import com.trading.coinflip.api.experiment.ExperimentSummaryResponse

data class ExperimentComparisonResponse(
    val experiments: List<ExperimentSummaryResponse>,
    val metrics: List<ComparisonMetricResponse>,
)