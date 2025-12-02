package com.trading.coinflip.live

import com.trading.coinflip.common.model.Timeframe
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("live_sessions")
data class LiveSessionEntity(
    @Id
    val id: Long? = null,
    val symbol: String,
    val timeframe: Timeframe = Timeframe.ONE_HOUR,
    var status: LiveSessionStatus = LiveSessionStatus.RUNNING,
    @Column("initial_capital")
    val initialCapital: BigDecimal,
    @Column("current_balance")
    var currentBalance: BigDecimal,
    @Column("peak_balance")
    var peakBalance: BigDecimal,
    @Column("max_drawdown")
    var maxDrawdown: BigDecimal = BigDecimal.ZERO,
    @Column("position_id_counter")
    var positionIdCounter: Long = 0,
    @Column("trade_id_counter")
    var tradeIdCounter: Long = 0,
    @Column("last_atr")
    var lastAtr: BigDecimal? = null,
    @Column("last_candle_close")
    var lastCandleClose: BigDecimal? = null,
    @Column("last_candle_time")
    var lastCandleTime: Instant? = null,
    @Column("started_at")
    val startedAt: Instant = Instant.now(),
    @Column("last_update_at")
    var lastUpdateAt: Instant = Instant.now(),
    @Column("stopped_at")
    var stoppedAt: Instant? = null,
    @Column("error_message")
    var errorMessage: String? = null,
    @Column("reconnect_count")
    var reconnectCount: Int = 0,
)
