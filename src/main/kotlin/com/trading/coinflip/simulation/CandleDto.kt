package com.trading.coinflip.simulation

import java.math.BigDecimal
import java.time.Instant

data class CandleDto(
    val openTime: Instant,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val atr: BigDecimal?,
)
