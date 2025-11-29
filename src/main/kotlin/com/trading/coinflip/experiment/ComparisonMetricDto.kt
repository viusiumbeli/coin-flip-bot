package com.trading.coinflip.experiment

data class ComparisonMetricDto(
    val label: String,
    val values: Map<Long, String>,
)
