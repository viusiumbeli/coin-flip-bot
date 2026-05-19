package com.trading.coinflip.exchange

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * Interface for receiving execution events from exchange via private WebSocket.
 * Used to detect when positions are closed by exchange-side mechanisms (trailing stop, etc).
 */
interface ExchangeExecutionClient {
    /**
     * Connect to private WebSocket and stream execution events.
     * Requires authentication with API credentials.
     */
    fun connectAndStream(scope: CoroutineScope): Flow<ExecutionEvent>

    /**
     * Stop the WebSocket connection.
     */
    fun stop()

    /**
     * Check if WebSocket is currently running.
     */
    fun isRunning(): Boolean
}

/**
 * Execution event from exchange.
 */
sealed class ExecutionEvent {
    /**
     * Position was closed (partially or fully) on the exchange.
     */
    data class PositionClosed(
        val symbol: String,
        val side: String, // "Buy" or "Sell"
        val closedSize: BigDecimal,
        val execPrice: BigDecimal,
        val execPnl: BigDecimal,
        val orderId: String,
        val execId: String,
    ) : ExecutionEvent()

    /**
     * Position size changed (opened, increased, or decreased).
     */
    data class PositionUpdate(
        val symbol: String,
        val side: String, // "Buy", "Sell", or "" (empty when closed)
        val size: BigDecimal,
        val entryPrice: BigDecimal,
        val unrealisedPnl: BigDecimal,
        val curRealisedPnl: BigDecimal,
        val positionIdx: Int,
    ) : ExecutionEvent()
}
