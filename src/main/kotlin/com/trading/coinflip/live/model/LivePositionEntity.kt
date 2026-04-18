package com.trading.coinflip.live.model

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.model.Position
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("live_positions")
data class LivePositionEntity(
    @Id
    val id: Long? = null,
    @Column("session_id")
    val sessionId: Long,
    @Column("position_id")
    val positionId: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    val status: PositionStatus = PositionStatus.OPEN,
    @Column("entry_time")
    val entryTime: Instant,
    @Column("entry_price")
    val entryPrice: BigDecimal,
    @Column("position_size")
    val positionSize: BigDecimal,
    @Column("initial_stop_loss")
    val initialStopLoss: BigDecimal,
    @Column("trailing_stop")
    var trailingStop: BigDecimal,
    @Column("highest_favorable_price")
    var highestFavorablePrice: BigDecimal,
    @Column("balance_before_open")
    val balanceBeforeOpen: BigDecimal,
    @Column("balance_after_open")
    val balanceAfterOpen: BigDecimal,
    @Column("allocated_capital")
    val allocatedCapital: BigDecimal,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    var updatedAt: Instant = Instant.now(),
) {
    fun toPosition(): Position =
        Position(
            id = positionId,
            symbol = symbol,
            timeframe = timeframe,
            side = side,
            entryTime = entryTime,
            entryPrice = entryPrice,
            positionSize = positionSize,
            initialStopLoss = initialStopLoss,
            trailingStop = trailingStop,
            highestFavorablePrice = highestFavorablePrice,
            status = status,
            balanceBeforeOpen = balanceBeforeOpen,
            balanceAfterOpen = balanceAfterOpen,
            allocatedCapital = allocatedCapital,
        )

    companion object {
        fun fromPosition(
            position: Position,
            sessionId: Long,
        ): LivePositionEntity =
            LivePositionEntity(
                sessionId = sessionId,
                positionId = position.id,
                symbol = position.symbol,
                timeframe = position.timeframe,
                side = position.side,
                status = position.status,
                entryTime = position.entryTime,
                entryPrice = position.entryPrice,
                positionSize = position.positionSize,
                initialStopLoss = position.initialStopLoss,
                trailingStop = position.trailingStop,
                highestFavorablePrice = position.highestFavorablePrice,
                balanceBeforeOpen = position.balanceBeforeOpen,
                balanceAfterOpen = position.balanceAfterOpen,
                allocatedCapital = position.allocatedCapital,
            )
    }
}