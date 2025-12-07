package com.trading.coinflip.exchange

import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Interface for exchange WebSocket clients.
 * Handles real-time kline/candlestick streaming.
 */
interface ExchangeWebSocketClient {
    /**
     * Connect to WebSocket and stream completed candles.
     * Automatically handles reconnection with exponential backoff.
     *
     * @param symbol Trading pair (e.g., "BTCUSDT")
     * @param timeframe Candle timeframe
     * @param scope Coroutine scope for the connection
     * @return Flow emitting completed candles only
     */
    fun connectAndStream(
        symbol: String,
        timeframe: Timeframe,
        scope: CoroutineScope,
    ): Flow<CandleEntity>

    /**
     * Stop the WebSocket connection gracefully.
     */
    fun stop()

    /**
     * Check if currently connected/running.
     */
    fun isRunning(): Boolean

    /**
     * Get current reconnect attempt count.
     */
    fun getReconnectAttempts(): Int
}
