package com.trading.coinflip.live.model

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.Trade
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("live_trades")
data class LiveTradeEntity(
    @Id
    val id: Long? = null,
    @Column("session_id")
    val sessionId: Long,
    @Column("trade_id")
    val tradeId: Long,
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
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        fun fromTrade(
            trade: Trade,
            sessionId: Long,
        ): LiveTradeEntity =
            LiveTradeEntity(
                sessionId = sessionId,
                tradeId = trade.id,
                symbol = trade.symbol,
                timeframe = trade.timeframe,
                side = trade.side,
                entryTime = trade.entryTime,
                entryPrice = trade.entryPrice,
                exitTime = trade.exitTime,
                exitPrice = trade.exitPrice,
                positionSize = trade.positionSize,
                initialStopLoss = trade.initialStopLoss,
                trailingStop = trade.trailingStop,
                profitLoss = trade.profitLoss,
                profitLossPercent = trade.profitLossPercent,
                exitReason = trade.exitReason,
                balanceBeforeOpen = trade.balanceBeforeOpen,
                balanceAfterOpen = trade.balanceAfterOpen,
                balanceBeforeClose = trade.balanceBeforeClose,
                balanceAfterClose = trade.balanceAfterClose,
            )
    }
}
