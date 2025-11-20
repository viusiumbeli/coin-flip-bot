package com.trading.coinflip.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

enum class Timeframe(val minutes: Int, val label: String) {
    ONE_HOUR(60, "1h"),
    FOUR_HOURS(240, "4h"),
    ONE_DAY(1440, "1d");

    companion object {
        fun fromLabel(label: String): Timeframe? = entries.find { it.label == label }
    }
}

enum class PositionSide {
    LONG, SHORT
}

enum class PositionStatus {
    OPEN, CLOSED
}

@Entity
@Table(name = "candles", indexes = [
    Index(name = "idx_candles_symbol_timeframe_time", columnList = "symbol,timeframe,openTime", unique = true)
])
data class Candle(
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
    var atr: BigDecimal? = null
)

data class Trade(
    val id: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val exitTime: Instant,
    val exitPrice: BigDecimal,
    val positionSize: BigDecimal,
    val initialStopLoss: BigDecimal,
    val trailingStop: BigDecimal,
    val profitLoss: BigDecimal,
    val profitLossPercent: BigDecimal,
    val exitReason: String
)

data class Position(
    val id: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val side: PositionSide,
    val entryTime: Instant,
    val entryPrice: BigDecimal,
    val positionSize: BigDecimal,
    val initialStopLoss: BigDecimal,
    var trailingStop: BigDecimal,
    var highestFavorablePrice: BigDecimal,
    var status: PositionStatus,
    var exitTime: Instant? = null,
    var exitPrice: BigDecimal? = null,
    var exitReason: String? = null
) {
    fun updateTrailingStop(currentPrice: BigDecimal, atr: BigDecimal, atrMultiplier: BigDecimal): Boolean {
        val isFavorableMove = when (side) {
            PositionSide.LONG -> currentPrice > highestFavorablePrice
            PositionSide.SHORT -> currentPrice < highestFavorablePrice
        }

        if (isFavorableMove) {
            highestFavorablePrice = currentPrice
            val newStop = when (side) {
                PositionSide.LONG -> currentPrice - (atr * atrMultiplier)
                PositionSide.SHORT -> currentPrice + (atr * atrMultiplier)
            }

            val shouldUpdate = when (side) {
                PositionSide.LONG -> newStop > trailingStop
                PositionSide.SHORT -> newStop < trailingStop
            }

            if (shouldUpdate) {
                trailingStop = newStop
                return true
            }
        }
        return false
    }

    fun isStopHit(currentPrice: BigDecimal): Boolean {
        return when (side) {
            PositionSide.LONG -> currentPrice <= trailingStop
            PositionSide.SHORT -> currentPrice >= trailingStop
        }
    }

    fun toTrade(tradeId: Long): Trade {
        require(status == PositionStatus.CLOSED) { "Position must be closed to convert to trade" }
        require(exitPrice != null && exitTime != null) { "Exit price and time must be set" }

        val pnl = when (side) {
            PositionSide.LONG -> (exitPrice!! - entryPrice) * positionSize
            PositionSide.SHORT -> (entryPrice - exitPrice!!) * positionSize
        }

        val pnlPercent = (pnl / (entryPrice * positionSize)) * BigDecimal(100)

        return Trade(
            id = tradeId,
            symbol = symbol,
            timeframe = timeframe,
            side = side,
            entryTime = entryTime,
            entryPrice = entryPrice,
            exitTime = exitTime!!,
            exitPrice = exitPrice!!,
            positionSize = positionSize,
            initialStopLoss = initialStopLoss,
            trailingStop = trailingStop,
            profitLoss = pnl,
            profitLossPercent = pnlPercent,
            exitReason = exitReason ?: "Unknown"
        )
    }
}

data class BacktestConfig(
    val symbol: String,
    val timeframe: Timeframe,
    val initialCapital: BigDecimal,
    val riskPerTrade: BigDecimal, // Percentage (e.g., 1.0 for 1%)
    val atrPeriod: Int = 10,
    val atrMultiplier: BigDecimal = BigDecimal("3.0"),
    val transactionCostPercent: BigDecimal = BigDecimal("0.1"), // 0.1%
    val maxConcurrentPositions: Int = 5,
    val startDate: Instant? = null,
    val endDate: Instant? = null
)

data class BacktestResult(
    val config: BacktestConfig,
    val trades: List<Trade>,
    val initialCapital: BigDecimal,
    val finalCapital: BigDecimal,
    val totalReturn: BigDecimal,
    val totalReturnPercent: BigDecimal,
    val maxDrawdown: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val winRate: BigDecimal,
    val profitFactor: BigDecimal,
    val sharpeRatio: BigDecimal,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val averageWin: BigDecimal,
    val averageLoss: BigDecimal,
    val largestWin: BigDecimal,
    val largestLoss: BigDecimal,
    val averageTradeDuration: Long, // in minutes
    val startDate: Instant,
    val endDate: Instant,
    val buyAndHoldReturn: BigDecimal,
    val buyAndHoldReturnPercent: BigDecimal
)
