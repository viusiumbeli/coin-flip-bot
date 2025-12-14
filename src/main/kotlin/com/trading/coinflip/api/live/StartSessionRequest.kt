package com.trading.coinflip.api.live

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.common.model.TrailingStopMode
import com.trading.coinflip.exchange.Exchange
import java.math.BigDecimal

data class StartSessionRequest(
    val symbol: String,
    val timeframe: Timeframe,
    val exchange: Exchange? = null, // Defaults to configured exchange if null
    val trailingStopMode: TrailingStopMode = TrailingStopMode.ATR,
    val trailingStopPercent: BigDecimal = BigDecimal("1.0"),
    val atrMultiplier: BigDecimal = BigDecimal("3.0"),
    val leverage: Int = 1,
)
