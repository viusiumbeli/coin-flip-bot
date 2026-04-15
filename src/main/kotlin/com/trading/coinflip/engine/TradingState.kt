package com.trading.coinflip.engine

import com.trading.coinflip.common.model.Trade
import com.trading.coinflip.engine.model.Position
import java.math.BigDecimal

/**
 * Mutable state container for trading operations.
 * Used by TradingProcessor to track account state during backtest/simulation.
 */
data class TradingState(
    var accountBalance: BigDecimal,
    var peakBalance: BigDecimal,
    var maxDrawdown: BigDecimal,
    val openPositions: MutableList<Position>,
    val closedTrades: MutableList<Trade>,
    var tradeIdCounter: Long,
    var positionIdCounter: Long,
) {
    companion object {
        fun create(initialCapital: BigDecimal): TradingState =
            TradingState(
                accountBalance = initialCapital,
                peakBalance = initialCapital,
                maxDrawdown = BigDecimal.ZERO,
                openPositions = mutableListOf(),
                closedTrades = mutableListOf(),
                tradeIdCounter = 0L,
                positionIdCounter = 0L,
            )
    }

    fun reset(initialCapital: BigDecimal) {
        accountBalance = initialCapital
        peakBalance = initialCapital
        maxDrawdown = BigDecimal.ZERO
        openPositions.clear()
        closedTrades.clear()
        tradeIdCounter = 0L
        positionIdCounter = 0L
    }
}
