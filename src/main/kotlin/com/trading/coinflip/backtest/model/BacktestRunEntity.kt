package com.trading.coinflip.backtest.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal

@Table("backtest_runs")
data class BacktestRunEntity(
    @Id
    val id: Long? = null,
    @Column("experiment_id")
    val experimentId: Long,
    @Column("run_number")
    val runNumber: Int,
    // Results for this individual run
    @Column("final_capital")
    val finalCapital: BigDecimal,
    @Column("total_return")
    val totalReturn: BigDecimal,
    @Column("total_return_percent")
    val totalReturnPercent: BigDecimal,
    @Column("max_drawdown")
    val maxDrawdown: BigDecimal,
    @Column("max_drawdown_percent")
    val maxDrawdownPercent: BigDecimal,
    @Column("win_rate")
    val winRate: BigDecimal,
    @Column("profit_factor")
    val profitFactor: BigDecimal,
    @Column("sharpe_ratio")
    val sharpeRatio: BigDecimal,
    @Column("total_trades")
    val totalTrades: Int,
    @Column("winning_trades")
    val winningTrades: Int,
    @Column("losing_trades")
    val losingTrades: Int,
    @Column("average_win")
    val averageWin: BigDecimal,
    @Column("average_loss")
    val averageLoss: BigDecimal,
    @Column("largest_win")
    val largestWin: BigDecimal,
    @Column("largest_loss")
    val largestLoss: BigDecimal,
    @Column("average_trade_duration")
    val averageTradeDuration: Long,
    @Column("buy_and_hold_return")
    val buyAndHoldReturn: BigDecimal,
    @Column("buy_and_hold_return_percent")
    val buyAndHoldReturnPercent: BigDecimal,
)