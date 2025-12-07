package com.trading.coinflip.exchange

import java.math.BigDecimal

/**
 * Interface for exchange trading operations.
 * Follows Interface Segregation - only trading-specific methods.
 * Market data operations remain in ExchangeClient.
 */
interface ExchangeTradingClient {
    // --- Order Operations ---

    /**
     * Place a new order on the exchange.
     * @return Order result with orderId
     */
    suspend fun placeOrder(request: PlaceOrderRequest): OrderResult

    /**
     * Cancel an existing order.
     * @return Cancelled order result
     */
    suspend fun cancelOrder(
        symbol: String,
        orderId: String,
    ): OrderResult

    /**
     * Amend an unfilled or partially filled order.
     * @return Amended order result
     */
    suspend fun amendOrder(request: AmendOrderRequest): OrderResult

    // --- Position Operations ---

    /**
     * Get current positions.
     * @param symbol Optional - specific symbol or null for all positions
     * @return List of open positions
     */
    suspend fun getPositions(symbol: String? = null): List<PositionInfo>

    /**
     * Set leverage for a symbol.
     * @return true if successful
     */
    suspend fun setLeverage(
        symbol: String,
        leverage: Int,
    ): Boolean

    /**
     * Set take profit and/or stop loss on existing position.
     * @return true if successful
     */
    suspend fun setTradingStop(request: TradingStopRequest): Boolean

    // --- Account Operations ---

    /**
     * Get wallet balance information.
     */
    suspend fun getWalletBalance(): WalletBalance
}

// --- Common Trading DTOs ---

enum class OrderSide { Buy, Sell }

enum class OrderType { Market, Limit }

/**
 * Request to place a new order.
 */
data class PlaceOrderRequest(
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val qty: BigDecimal,
    val price: BigDecimal? = null, // Required for Limit orders
    val takeProfit: BigDecimal? = null,
    val stopLoss: BigDecimal? = null,
    val reduceOnly: Boolean = false,
    val timeInForce: String = "GTC", // GTC, IOC, FOK, PostOnly
)

/**
 * Request to amend an existing order.
 */
data class AmendOrderRequest(
    val symbol: String,
    val orderId: String,
    val qty: BigDecimal? = null,
    val price: BigDecimal? = null,
    val takeProfit: BigDecimal? = null,
    val stopLoss: BigDecimal? = null,
)

/**
 * Request to set trading stop on position.
 */
data class TradingStopRequest(
    val symbol: String,
    val takeProfit: BigDecimal? = null, // Pass BigDecimal.ZERO to cancel
    val stopLoss: BigDecimal? = null, // Pass BigDecimal.ZERO to cancel
    val trailingStop: BigDecimal? = null,
    val tpslMode: TpslMode = TpslMode.Full,
)

enum class TpslMode { Full, Partial }

/**
 * Result of order operations (place, cancel, amend).
 */
data class OrderResult(
    val orderId: String,
    val orderLinkId: String = "",
)

/**
 * Position information from exchange.
 */
data class PositionInfo(
    val symbol: String,
    val side: String, // Buy, Sell, or None
    val size: BigDecimal,
    val avgPrice: BigDecimal,
    val leverage: String,
    val unrealisedPnl: BigDecimal,
    val takeProfit: BigDecimal?,
    val stopLoss: BigDecimal?,
    val liqPrice: BigDecimal?,
    val positionValue: BigDecimal,
)

/**
 * Wallet balance information.
 */
data class WalletBalance(
    val totalEquity: BigDecimal,
    val totalWalletBalance: BigDecimal,
    val totalAvailableBalance: BigDecimal,
    val totalUnrealisedPnl: BigDecimal,
    val coins: List<CoinBalance>,
)

/**
 * Per-coin balance details.
 */
data class CoinBalance(
    val coin: String,
    val walletBalance: BigDecimal,
    val availableBalance: BigDecimal,
    val unrealisedPnl: BigDecimal,
)
