package com.trading.coinflip.data

import com.trading.coinflip.common.model.Timeframe
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("candles")
data class CandleEntity(
    @Id
    val id: Long? = null,
    val symbol: String,
    val timeframe: Timeframe,
    @Column("open_time")
    val openTime: Instant,
    @Column("open")
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val atr: BigDecimal? = null,
)
