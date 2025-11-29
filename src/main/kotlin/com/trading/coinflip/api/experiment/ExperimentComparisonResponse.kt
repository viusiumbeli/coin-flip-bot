package com.trading.coinflip.api.experiment

data class ExperimentComparisonResponse(
    val experiments: List<ExperimentSummaryResponse>,
    val metrics: List<ComparisonMetricResponse>,
)
