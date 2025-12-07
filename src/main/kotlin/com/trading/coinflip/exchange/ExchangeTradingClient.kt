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

    /**
     * Switch position mode for a symbol.
     * @param symbol Trading symbol (e.g., "BTCUSDT")
     * @param hedgeMode true for Hedge Mode (both sides), false for One-Way (netting) Mode
     * @return true if switch was successful or mode already set
     */
    suspend fun switchPositionMode(
        symbol: String,
        hedgeMode: Boolean,
    ): Boolean
}

// --- Common Trading DTOs ---

enum class OrderSide { Buy, Sell }

enum class OrderType { Market, Limit }

/**
 * Position index for Hedge Mode.
 * One-Way mode: use OneWay (0)
 * Hedge mode: use HedgeLong (1) for Buy side, HedgeShort (2) for Sell side
 */
enum class PositionIdx(
    val value: Int,
) {
    OneWay(0),
    HedgeLong(1),
    HedgeShort(2),
}

/**
 * Request to place a new order.
 * For conditional stop orders, set triggerPrice and triggerDirection.
 */
data class PlaceOrderRequest(
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val qty: BigDecimal,
    val price: BigDecimal? = null, // Required for Limit orders
    val takeProfit: BigDecimal? = null,
    val stopLoss: BigDecimal? = null, // Position-level SL (merges with other positions)
    val reduceOnly: Boolean = false,
    val timeInForce: String = "GTC", // GTC, IOC, FOK, PostOnly
    val positionIdx: PositionIdx = PositionIdx.HedgeLong, // Default to Hedge mode
    // Conditional order fields - creates a stop order that doesn't merge
    val triggerPrice: BigDecimal? = null, // Price at which order triggers
    val triggerDirection: TriggerDirection? = null, // Rise or fall to triggerPrice
    val orderLinkId: String? = null, // Custom ID for tracking (e.g., "stop_pos_123")
)

/**
 * Trigger direction for conditional orders.
 */
enum class TriggerDirection(val value: Int) {
    RiseTo(1),  // Trigger when price rises to triggerPrice (for take profit on short)
    FallTo(2),  // Trigger when price falls to triggerPrice (for stop loss on long)
}

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
    val triggerPrice: BigDecimal? = null, // For amending conditional stop orders
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
    val positionIdx: PositionIdx = PositionIdx.HedgeLong, // Default to Hedge mode
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
