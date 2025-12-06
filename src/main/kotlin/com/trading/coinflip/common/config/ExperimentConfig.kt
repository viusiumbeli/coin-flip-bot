package com.trading.coinflip.common.config

data class ExperimentConfig(
    var syncBacktestLimit: Int = 1_000_000,
    var asyncBacktestLimit: Int = 10_000_000,
)
