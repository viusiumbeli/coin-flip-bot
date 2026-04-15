package com.trading.coinflip.common.model

import com.trading.coinflip.engine.model.PositionSide
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("experiment_trades")
data class ExperimentTradeEntity(
    @Id
    val id: Long? = null,
    @Column("backtest_run_id")
    val backtestRunId: Long,
    @Column("trade_number")
    val tradeNumber: Int,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    @Column("entry_time")
    val entryTime: Instant,
    @Column("entry_price")
    val entryPrice: BigDecimal,
    @Column("exit_time")
    val exitTime: Instant,
    @Column("exit_price")
    val exitPrice: BigDecimal,
    @Column("position_size")
    val positionSize: BigDecimal,
    @Column("initial_stop_loss")
    val initialStopLoss: BigDecimal,
    @Column("trailing_stop")
    val trailingStop: BigDecimal,
    @Column("profit_loss")
    val profitLoss: BigDecimal,
    @Column("profit_loss_percent")
    val profitLossPercent: BigDecimal,
    @Column("exit_reason")
    val exitReason: String,
    @Column("balance_before_open")
    val balanceBeforeOpen: BigDecimal,
    @Column("balance_after_open")
    val balanceAfterOpen: BigDecimal,
    @Column("balance_before_close")
    val balanceBeforeClose: BigDecimal,
    @Column("balance_after_close")
    val balanceAfterClose: BigDecimal,
)
