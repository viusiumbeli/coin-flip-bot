package com.trading.coinflip.trading

import org.springframework.stereotype.Component

/**
 * Factory for creating TradingProcessor instances.
 * Thread-safe: each processor uses stateless CoinFlipStrategy object.
 */
@Component
class TradingProcessorFactory {
    fun create(): TradingProcessor = TradingProcessor()
}
