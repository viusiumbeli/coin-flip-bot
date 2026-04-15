package com.trading.coinflip.common.model

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
    name = "experiments",
    indexes = [
        Index(name = "idx_experiments_symbol_timeframe", columnList = "symbol,timeframe"),
        Index(name = "idx_experiments_created_at", columnList = "createdAt"),
    ],
)
data class ExperimentEntity(
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
    var startDate: Instant,
    @Column(name = "end_date", nullable = false)
    var endDate: Instant,
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
    // Aggregated Results (averages across all runs) - mutable for async updates
    @Column(name = "final_capital", nullable = false, precision = 20, scale = 8)
    var finalCapital: BigDecimal,
    @Column(name = "total_return", nullable = false, precision = 20, scale = 8)
    var totalReturn: BigDecimal,
    @Column(name = "total_return_percent", nullable = false, precision = 20, scale = 8)
    var totalReturnPercent: BigDecimal,
    @Column(name = "max_drawdown", nullable = false, precision = 20, scale = 8)
    var maxDrawdown: BigDecimal,
    @Column(name = "max_drawdown_percent", nullable = false, precision = 20, scale = 8)
    var maxDrawdownPercent: BigDecimal,
    @Column(name = "win_rate", nullable = false, precision = 10, scale = 4)
    var winRate: BigDecimal,
    @Column(name = "profit_factor", nullable = false, precision = 20, scale = 8)
    var profitFactor: BigDecimal,
    @Column(name = "sharpe_ratio", nullable = false, precision = 20, scale = 8)
    var sharpeRatio: BigDecimal,
    @Column(name = "total_trades", nullable = false)
    var totalTrades: Int,
    @Column(name = "winning_trades", nullable = false)
    var winningTrades: Int,
    @Column(name = "losing_trades", nullable = false)
    var losingTrades: Int,
    @Column(name = "average_win", nullable = false, precision = 20, scale = 8)
    var averageWin: BigDecimal,
    @Column(name = "average_loss", nullable = false, precision = 20, scale = 8)
    var averageLoss: BigDecimal,
    @Column(name = "largest_win", nullable = false, precision = 20, scale = 8)
    var largestWin: BigDecimal,
    @Column(name = "largest_loss", nullable = false, precision = 20, scale = 8)
    var largestLoss: BigDecimal,
    @Column(name = "average_trade_duration", nullable = false)
    var averageTradeDuration: Long,
    @Column(name = "buy_and_hold_return", nullable = false, precision = 20, scale = 8)
    var buyAndHoldReturn: BigDecimal,
    @Column(name = "buy_and_hold_return_percent", nullable = false, precision = 20, scale = 8)
    var buyAndHoldReturnPercent: BigDecimal,
    @Column(name = "runs_beat_buy_hold", nullable = false)
    var runsBeatBuyHold: Int = 0,
    // Variance/distribution metrics for totalReturnPercent (nullable for backward compatibility)
    @Column(name = "return_std_dev", precision = 20, scale = 8)
    var returnStdDev: BigDecimal? = null,
    @Column(name = "return_min", precision = 20, scale = 8)
    var returnMin: BigDecimal? = null,
    @Column(name = "return_max", precision = 20, scale = 8)
    var returnMax: BigDecimal? = null,
    @Column(name = "return_p5", precision = 20, scale = 8)
    var returnP5: BigDecimal? = null,
    @Column(name = "return_p25", precision = 20, scale = 8)
    var returnP25: BigDecimal? = null,
    @Column(name = "return_p50", precision = 20, scale = 8)
    var returnP50: BigDecimal? = null,
    @Column(name = "return_p75", precision = 20, scale = 8)
    var returnP75: BigDecimal? = null,
    @Column(name = "return_p95", precision = 20, scale = 8)
    var returnP95: BigDecimal? = null,
    // Async execution status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ExperimentStatus = ExperimentStatus.COMPLETED,
    @Column(name = "completed_runs", nullable = false)
    var completedRuns: Int = 0,
    @Column(name = "failed_runs", nullable = false)
    var failedRuns: Int = 0,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "finished_at")
    var finishedAt: Instant? = null,
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
)
