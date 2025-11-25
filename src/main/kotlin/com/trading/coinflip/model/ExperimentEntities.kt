package com.trading.coinflip.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "experiments", indexes = [
    Index(name = "idx_experiments_symbol_timeframe", columnList = "symbol,timeframe"),
    Index(name = "idx_experiments_created_at", columnList = "createdAt")
])
data class Experiment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column(name = "custom_name")
    val customName: String? = null,

    @Column(columnDefinition = "TEXT")
    val notes: String? = null,

    @Column(nullable = false)
    val symbol: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val timeframe: Timeframe,

    @Column(name = "start_date", nullable = false)
    val startDate: Instant,

    @Column(name = "end_date", nullable = false)
    val endDate: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "num_backtests", nullable = false)
    val numBacktests: Int = 1,

    // Configuration
    @Column(name = "initial_capital", nullable = false, precision = 20, scale = 8)
    val initialCapital: BigDecimal,

    @Column(name = "risk_per_trade", nullable = false, precision = 10, scale = 4)
    val riskPerTrade: BigDecimal,

    @Column(name = "atr_period", nullable = false)
    val atrPeriod: Int,

    @Column(name = "atr_multiplier", nullable = false, precision = 10, scale = 4)
    val atrMultiplier: BigDecimal,

    @Column(name = "transaction_cost_percent", nullable = false, precision = 10, scale = 4)
    val transactionCostPercent: BigDecimal,

    @Column(name = "max_concurrent_positions", nullable = false)
    val maxConcurrentPositions: Int,

    // Aggregated Results (averages across all runs)
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
    val buyAndHoldReturnPercent: BigDecimal
)

@Entity
@Table(name = "backtest_runs", indexes = [
    Index(name = "idx_backtest_runs_experiment_id", columnList = "experiment_id")
])
data class BacktestRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    val experiment: Experiment,

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
    val buyAndHoldReturnPercent: BigDecimal
)

@Entity
@Table(name = "experiment_trades", indexes = [
    Index(name = "idx_experiment_trades_backtest_run_id", columnList = "backtest_run_id"),
    Index(name = "idx_experiment_trades_entry_time", columnList = "entryTime")
])
data class ExperimentTrade(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backtest_run_id", nullable = false)
    val backtestRun: BacktestRun,

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
    val balanceAfterClose: BigDecimal
)
