package com.trading.coinflip.common.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(
    name = "backtest_runs",
    indexes = [
        Index(name = "idx_backtest_runs_experiment_id", columnList = "experiment_id"),
    ],
)
data class BacktestRunEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    val experiment: ExperimentEntity,
    @Column(name = "run_number", nullable = false)
    val runNumber: Int,
    // Results for this individual run
    @Column(name = "final_capital", nullable = false, precision = 20, scale = 8)
    val finalCapital: BigDecimal,
    @Column(name = "total_return", nullable = false, precision = 20, scale = 8)
    val totalReturn: BigDecimal,
    @Column(name = "total_return_percent", nullable = false, precision = 20, scale = 8)
    val totalReturnPercent: BigDecimal,
    @Column(name = "max_drawdown", nullable = false, precision = 20, scale = 8)
    val maxDrawdown: BigDecimal,
    @Column(name = "max_drawdown_percent", nullable = false, precision = 20, scale = 8)
    val maxDrawdownPercent: BigDecimal,
    @Column(name = "win_rate", nullable = false, precision = 10, scale = 4)
    val winRate: BigDecimal,
    @Column(name = "profit_factor", nullable = false, precision = 20, scale = 8)
    val profitFactor: BigDecimal,
    @Column(name = "sharpe_ratio", nullable = false, precision = 20, scale = 8)
    val sharpeRatio: BigDecimal,
    @Column(name = "total_trades", nullable = false)
    val totalTrades: Int,
    @Column(name = "winning_trades", nullable = false)
    val winningTrades: Int,
    @Column(name = "losing_trades", nullable = false)
    val losingTrades: Int,
    @Column(name = "average_win", nullable = false, precision = 20, scale = 8)
    val averageWin: BigDecimal,
    @Column(name = "average_loss", nullable = false, precision = 20, scale = 8)
    val averageLoss: BigDecimal,
    @Column(name = "largest_win", nullable = false, precision = 20, scale = 8)
    val largestWin: BigDecimal,
    @Column(name = "largest_loss", nullable = false, precision = 20, scale = 8)
    val largestLoss: BigDecimal,
    @Column(name = "average_trade_duration", nullable = false)
    val averageTradeDuration: Long,
    @Column(name = "buy_and_hold_return", nullable = false, precision = 20, scale = 8)
    val buyAndHoldReturn: BigDecimal,
    @Column(name = "buy_and_hold_return_percent", nullable = false, precision = 20, scale = 8)
    val buyAndHoldReturnPercent: BigDecimal,
)
