package com.trading.coinflip.live.model

import java.math.BigDecimal

data class LiveConfigResponse(
    val enabled: Boolean,
    val symbols: List<String>,
    val initialCapital: BigDecimal,
    val websocketUrl: String,
)
