package com.trading.coinflip.trading

import com.trading.coinflip.strategy.CoinFlipStrategy
import org.springframework.stereotype.Component

/**
 * Factory for creating TradingProcessor instances.
 *
 * Each processor instance has its own CoinFlipStrategy with independent random state,
 * making it safe for parallel execution in coroutines.
 */
@Component
class TradingProcessorFactory {
    /**
     * Creates a new TradingProcessor instance with an independent CoinFlipStrategy.
     * Thread-safe: each call returns a new instance with no shared mutable state.
     */
    fun create(): TradingProcessor {
        val strategy = CoinFlipStrategy()
        return TradingProcessor(strategy)
    }
}
