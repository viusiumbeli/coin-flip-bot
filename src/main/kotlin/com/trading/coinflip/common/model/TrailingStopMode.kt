package com.trading.coinflip.common.model

/**
 * Mode for calculating trailing stop distance.
 */
enum class TrailingStopMode {
    /**
     * Distance = ATR × atrMultiplier
     * Dynamic based on market volatility
     */
    ATR,

    /**
     * Distance = price × trailingStopPercent / 100
     * Fixed percentage from current price
     */
    PERCENT,
}
