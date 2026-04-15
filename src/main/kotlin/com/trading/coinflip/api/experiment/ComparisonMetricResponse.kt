package com.trading.coinflip.api.experiment

data class ComparisonMetricResponse(
    val label: String,
    val values: Map<Long, String>,
)
