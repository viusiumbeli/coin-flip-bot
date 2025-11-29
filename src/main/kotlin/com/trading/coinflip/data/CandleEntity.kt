package com.trading.coinflip.data

import com.trading.coinflip.common.model.Timeframe
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "candles",
    indexes = [
        Index(name = "idx_candles_symbol_timeframe_time", columnList = "symbol,timeframe,openTime", unique = true),
    ],
)
data class CandleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val symbol: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val timeframe: Timeframe,
    @Column(nullable = false)
    val openTime: Instant,
    @Column(nullable = false, precision = 20, scale = 8)
    val open: BigDecimal,
    @Column(nullable = false, precision = 20, scale = 8)
    val high: BigDecimal,
    @Column(nullable = false, precision = 20, scale = 8)
    val low: BigDecimal,
    @Column(nullable = false, precision = 20, scale = 8)
    val close: BigDecimal,
    @Column(nullable = false, precision = 20, scale = 8)
    val volume: BigDecimal,
    @Column(precision = 20, scale = 8)
    var atr: BigDecimal? = null,
)
