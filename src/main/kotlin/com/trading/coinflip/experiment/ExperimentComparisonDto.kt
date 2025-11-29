package com.trading.coinflip.experiment

data class ExperimentComparisonDto(
    val experiments: List<ExperimentSummaryDto>,
    val metrics: List<ComparisonMetricDto>,
)
