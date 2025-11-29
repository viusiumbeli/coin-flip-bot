package com.trading.coinflip.common.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "experiment_trades",
    indexes = [
        Index(name = "idx_experiment_trades_backtest_run_id", columnList = "backtest_run_id"),
        Index(name = "idx_experiment_trades_entry_time", columnList = "entryTime"),
    ],
)
data class ExperimentTradeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backtest_run_id", nullable = false)
    val backtestRun: BacktestRunEntity,
    @Column(name = "trade_number", nullable = false)
    val tradeNumber: Int,
    @Column(nullable = false)
    val symbol: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val timeframe: Timeframe,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val side: PositionSide,
    @Column(name = "entry_time", nullable = false)
    val entryTime: Instant,
    @Column(name = "entry_price", nullable = false, precision = 20, scale = 8)
    val entryPrice: BigDecimal,
    @Column(name = "exit_time", nullable = false)
    val exitTime: Instant,
    @Column(name = "exit_price", nullable = false, precision = 20, scale = 8)
    val exitPrice: BigDecimal,
    @Column(name = "position_size", nullable = false, precision = 20, scale = 8)
    val positionSize: BigDecimal,
    @Column(name = "initial_stop_loss", nullable = false, precision = 20, scale = 8)
    val initialStopLoss: BigDecimal,
    @Column(name = "trailing_stop", nullable = false, precision = 20, scale = 8)
    val trailingStop: BigDecimal,
    @Column(name = "profit_loss", nullable = false, precision = 20, scale = 8)
    val profitLoss: BigDecimal,
    @Column(name = "profit_loss_percent", nullable = false, precision = 20, scale = 8)
    val profitLossPercent: BigDecimal,
    @Column(name = "exit_reason", nullable = false)
    val exitReason: String,
    @Column(name = "balance_before_open", nullable = false, precision = 20, scale = 8)
    val balanceBeforeOpen: BigDecimal,
    @Column(name = "balance_after_open", nullable = false, precision = 20, scale = 8)
    val balanceAfterOpen: BigDecimal,
    @Column(name = "balance_before_close", nullable = false, precision = 20, scale = 8)
    val balanceBeforeClose: BigDecimal,
    @Column(name = "balance_after_close", nullable = false, precision = 20, scale = 8)
    val balanceAfterClose: BigDecimal,
)
