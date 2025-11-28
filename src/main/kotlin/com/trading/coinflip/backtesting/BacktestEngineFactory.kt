package com.trading.coinflip.backtesting

import com.trading.coinflip.analytics.PerformanceAnalytics
import com.trading.coinflip.trading.TradingProcessorFactory
import org.springframework.stereotype.Component

/**
 * Factory for creating BacktestEngine instances.
 *
 * Each engine instance has its own TradingProcessor with independent random state,
 * making it safe for parallel execution in coroutines.
 */
@Component
class BacktestEngineFactory(
    private val analytics: PerformanceAnalytics,
    private val tradingProcessorFactory: TradingProcessorFactory,
) {
    /**
     * Creates a new BacktestEngine instance with an independent TradingProcessor.
     * Thread-safe: each call returns a new instance with no shared mutable state.
     */
    fun create(): BacktestEngine {
        val processor = tradingProcessorFactory.create()
        return BacktestEngine(processor, analytics)
    }
}
