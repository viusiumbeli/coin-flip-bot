package com.trading.coinflip.common.config

data class ApiConfig(
    var maxPageSize: Int = 1000,
    var httpTimeoutMs: Long = 30_000,
    var rateLimitDelayMs: Long = 100,
)
