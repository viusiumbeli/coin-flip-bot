package com.trading.coinflip.backtesting

import com.trading.coinflip.analytics.PerformanceAnalytics
import com.trading.coinflip.strategy.ATRCalculator
import com.trading.coinflip.strategy.CoinFlipStrategy
import org.springframework.stereotype.Component

/**
 * Factory for creating BacktestEngine instances.
 *
 * Each engine instance has its own CoinFlipStrategy with independent random state,
 * making it safe for parallel execution in coroutines.
 */
@Component
class BacktestEngineFactory(
    private val analytics: PerformanceAnalytics,
) {
    /**
     * Creates a new BacktestEngine instance with an independent CoinFlipStrategy.
     * Thread-safe: each call returns a new instance with no shared mutable state.
     */
    fun create(): BacktestEngine {
        val atrCalculator = ATRCalculator()
        val strategy = CoinFlipStrategy(atrCalculator)
        return BacktestEngine(strategy, analytics)
    }
}
