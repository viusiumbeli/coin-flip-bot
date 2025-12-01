package com.trading.coinflip.engine.model

import com.trading.coinflip.common.model.Trade
import java.math.BigDecimal

/**
 * Read-only view of trading state.
 * Both immutable TradingState and mutable MutableTradingState implement this interface.
 */
interface TradingStateView {
    val accountBalance: BigDecimal
    val peakBalance: BigDecimal
    val maxDrawdown: BigDecimal
    val openPositions: List<PositionView>
    val closedTrades: List<Trade>
    val tradeIdCounter: Long
    val positionIdCounter: Long
}
