package com.trading.coinflip.common.config

data class AsyncConfig(
    var parallelismMin: Int = 4,
    var parallelismMax: Int = 32,
    var channelCapacity: Int = 1000,
    var batchSize: Int = 1000,
    var shutdownTimeoutMs: Long = 30_000,
    var progressLogInterval: Int = 10_000,
)
