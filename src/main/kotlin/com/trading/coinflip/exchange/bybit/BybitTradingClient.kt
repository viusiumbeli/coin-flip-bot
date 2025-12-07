package com.trading.coinflip.exchange.bybit

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.coinflip.exchange.AmendOrderRequest
import com.trading.coinflip.exchange.CoinBalance
import com.trading.coinflip.exchange.ExchangeTradingClient
import com.trading.coinflip.exchange.OrderResult
import com.trading.coinflip.exchange.PlaceOrderRequest
import com.trading.coinflip.exchange.PositionInfo
import com.trading.coinflip.exchange.TradingStopRequest
import com.trading.coinflip.exchange.WalletBalance
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import mu.KotlinLogging
import java.math.BigDecimal

/**
 * Bybit V5 API trading client for linear perpetual contracts.
 * Implements ExchangeTradingClient interface for exchange abstraction.
 *
 * Supports both demo (api-demo.bybit.com) and mainnet (api.bybit.com).
 * All trading operations require authenticated requests via BybitAuthenticator.
 */
class BybitTradingClient(
    private val objectMapper: ObjectMapper,
    private val config: BybitTradingConfig,
    private val authenticator: BybitAuthenticator,
) : ExchangeTradingClient {
    private val log = KotlinLogging.logger {}

    private val client =
        HttpClient(CIO) {
            engine {
                requestTimeout = config.httpTimeoutMs
            }
        }

    // --- Order Operations ---

    override suspend fun placeOrder(request: PlaceOrderRequest): OrderResult {
        val body = buildOrderBody(request)
        log.info { "Placing order: ${request.symbol} ${request.side} ${request.orderType} qty=${request.qty}" }

        val response = post("/v5/order/create", body)
        return parseOrderResult(response)
    }

    override suspend fun cancelOrder(
        symbol: String,
        orderId: String,
    ): OrderResult {
        val body =
            objectMapper.writeValueAsString(
                mapOf(
                    "category" to "linear",
                    "symbol" to symbol,
                    "orderId" to orderId,
                ),
            )
        log.info { "Cancelling order: $symbol orderId=$orderId" }

        val response = post("/v5/order/cancel", body)
        return parseOrderResult(response)
    }

    override suspend fun amendOrder(request: AmendOrderRequest): OrderResult {
        val params =
            mutableMapOf<String, Any>(
                "category" to "linear",
                "symbol" to request.symbol,
                "orderId" to request.orderId,
            )
        request.qty?.let { params["qty"] = it.toPlainString() }
        request.price?.let { params["price"] = it.toPlainString() }
        request.takeProfit?.let { params["takeProfit"] = it.toPlainString() }
        request.stopLoss?.let { params["stopLoss"] = it.toPlainString() }

        val body = objectMapper.writeValueAsString(params)
        log.info { "Amending order: ${request.symbol} orderId=${request.orderId}" }

        val response = post("/v5/order/amend", body)
        return parseOrderResult(response)
    }

    // --- Position Operations ---

    override suspend fun getPositions(symbol: String?): List<PositionInfo> {
        val params =
            buildString {
                append("category=linear")
                if (symbol != null) {
                    append("&symbol=$symbol")
                } else {
                    append("&settleCoin=USDT")
                }
            }

        val response = get("/v5/position/list", params)
        return parsePositions(response)
    }

    override suspend fun setLeverage(
        symbol: String,
        leverage: Int,
    ): Boolean {
        val body =
            objectMapper.writeValueAsString(
                mapOf(
                    "category" to "linear",
                    "symbol" to symbol,
                    "buyLeverage" to leverage.toString(),
                    "sellLeverage" to leverage.toString(), // Must match in one-way mode
                ),
            )
        log.info { "Setting leverage: $symbol leverage=$leverage" }

        val response = post("/v5/position/set-leverage", body)
        return isSuccess(response)
    }

    override suspend fun setTradingStop(request: TradingStopRequest): Boolean {
        val params =
            mutableMapOf<String, Any>(
                "category" to "linear",
                "symbol" to request.symbol,
                "positionIdx" to 0, // One-way mode
                "tpslMode" to request.tpslMode.name,
            )
        request.takeProfit?.let { params["takeProfit"] = roundPrice(request.symbol, it).toPlainString() }
        request.stopLoss?.let { params["stopLoss"] = roundPrice(request.symbol, it).toPlainString() }
        request.trailingStop?.let { params["trailingStop"] = it.toPlainString() }

        // Use LastPrice as default trigger
        if (request.takeProfit != null) params["tpTriggerBy"] = "LastPrice"
        if (request.stopLoss != null) params["slTriggerBy"] = "LastPrice"

        val body = objectMapper.writeValueAsString(params)
        log.info { "Setting trading stop: ${request.symbol} TP=${request.takeProfit} SL=${request.stopLoss}" }

        val response = post("/v5/position/trading-stop", body)
        return isSuccess(response)
    }

    // --- Account Operations ---

    override suspend fun getWalletBalance(): WalletBalance {
        val params = "accountType=UNIFIED"
        val response = get("/v5/account/wallet-balance", params)
        return parseWalletBalance(response)
    }

    // --- HTTP Methods ---

    private suspend fun post(
        endpoint: String,
        body: String,
    ): JsonNode {
        val url = "${config.baseUrl}$endpoint"
        val headers = authenticator.generateHeaders(body)

        val response: HttpResponse =
            client.post(url) {
                contentType(ContentType.Application.Json)
                headers {
                    headers.forEach { (key, value) -> append(key, value) }
                }
                setBody(body)
            }

        val responseBody = response.bodyAsText()
        val root = objectMapper.readTree(responseBody)

        // Check for API errors
        val retCode = root["retCode"]?.asInt() ?: -1
        if (retCode != 0) {
            val retMsg = root["retMsg"]?.asText() ?: "Unknown error"
            log.error { "Bybit API error on $endpoint: $retCode - $retMsg" }
            throw BybitApiException(retCode, retMsg)
        }

        return root
    }

    private suspend fun get(
        endpoint: String,
        queryParams: String,
    ): JsonNode {
        val url = "${config.baseUrl}$endpoint?$queryParams"
        val headers = authenticator.generateHeaders(queryParams)

        val response: HttpResponse =
            client.get(url) {
                headers {
                    headers.forEach { (key, value) -> append(key, value) }
                }
            }

        val responseBody = response.bodyAsText()
        val root = objectMapper.readTree(responseBody)

        // Check for API errors
        val retCode = root["retCode"]?.asInt() ?: -1
        if (retCode != 0) {
            val retMsg = root["retMsg"]?.asText() ?: "Unknown error"
            log.error { "Bybit API error on $endpoint: $retCode - $retMsg" }
            throw BybitApiException(retCode, retMsg)
        }

        return root
    }

    // --- Response Parsing ---

    /**
     * Round quantity to valid precision for the symbol.
     * Bybit requires specific decimal places per instrument.
     */
    private fun roundQty(
        symbol: String,
        qty: BigDecimal,
    ): BigDecimal {
        // Qty step sizes for common USDT perpetuals
        val scale =
            when {
                symbol.startsWith("BTC") -> 3 // 0.001 BTC
                symbol.startsWith("ETH") -> 2 // 0.01 ETH
                symbol.startsWith("BNB") -> 2 // 0.01 BNB
                symbol.startsWith("SOL") -> 1 // 0.1 SOL
                symbol.startsWith("XRP") -> 0 // 1 XRP
                symbol.startsWith("DOGE") -> 0 // 1 DOGE
                else -> 3 // Default to 3 decimals
            }
        return qty.setScale(scale, java.math.RoundingMode.DOWN)
    }

    /**
     * Round price to valid precision for the symbol.
     */
    private fun roundPrice(
        symbol: String,
        price: BigDecimal,
    ): BigDecimal {
        // Price tick sizes for common USDT perpetuals
        val scale =
            when {
                symbol.startsWith("BTC") -> 1 // 0.1 USDT
                symbol.startsWith("ETH") -> 2 // 0.01 USDT
                symbol.startsWith("BNB") -> 2 // 0.01 USDT
                symbol.startsWith("SOL") -> 2 // 0.01 USDT
                else -> 2 // Default to 2 decimals
            }
        return price.setScale(scale, java.math.RoundingMode.DOWN)
    }

    private fun buildOrderBody(request: PlaceOrderRequest): String {
        val roundedQty = roundQty(request.symbol, request.qty)

        val params =
            mutableMapOf<String, Any>(
                "category" to "linear",
                "symbol" to request.symbol,
                "side" to request.side.name,
                "orderType" to request.orderType.name,
                "qty" to roundedQty.toPlainString(),
                "positionIdx" to 0, // One-way mode
                "timeInForce" to request.timeInForce,
            )

        request.price?.let { params["price"] = roundPrice(request.symbol, it).toPlainString() }
        request.takeProfit?.let { params["takeProfit"] = roundPrice(request.symbol, it).toPlainString() }
        request.stopLoss?.let { params["stopLoss"] = roundPrice(request.symbol, it).toPlainString() }
        if (request.reduceOnly) params["reduceOnly"] = true

        // Set TP/SL triggers to LastPrice
        if (request.takeProfit != null) {
            params["tpTriggerBy"] = "LastPrice"
            params["tpOrderType"] = "Market"
        }
        if (request.stopLoss != null) {
            params["slTriggerBy"] = "LastPrice"
            params["slOrderType"] = "Market"
        }

        return objectMapper.writeValueAsString(params)
    }

    private fun parseOrderResult(root: JsonNode): OrderResult {
        val result = root["result"]
        return OrderResult(
            orderId = result["orderId"]?.asText() ?: "",
            orderLinkId = result["orderLinkId"]?.asText() ?: "",
        )
    }

    private fun parsePositions(root: JsonNode): List<PositionInfo> {
        val list = root["result"]?.get("list") ?: return emptyList()

        return list.mapNotNull { pos ->
            val size = pos["size"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            // Skip empty positions
            if (size == BigDecimal.ZERO) return@mapNotNull null

            PositionInfo(
                symbol = pos["symbol"]?.asText() ?: "",
                side = pos["side"]?.asText() ?: "None",
                size = size,
                avgPrice = pos["avgPrice"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                leverage = pos["leverage"]?.asText() ?: "1",
                unrealisedPnl = pos["unrealisedPnl"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                takeProfit = pos["takeProfit"]?.asText()?.toBigDecimalOrNull(),
                stopLoss = pos["stopLoss"]?.asText()?.toBigDecimalOrNull(),
                liqPrice = pos["liqPrice"]?.asText()?.toBigDecimalOrNull(),
                positionValue = pos["positionValue"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            )
        }
    }

    private fun parseWalletBalance(root: JsonNode): WalletBalance {
        val list = root["result"]?.get("list")
        if (list == null || !list.isArray || list.isEmpty) {
            return WalletBalance(
                totalEquity = BigDecimal.ZERO,
                totalWalletBalance = BigDecimal.ZERO,
                totalAvailableBalance = BigDecimal.ZERO,
                totalUnrealisedPnl = BigDecimal.ZERO,
                coins = emptyList(),
            )
        }

        val account = list[0] // First (and usually only) account
        val coins =
            account["coin"]?.map { coin ->
                CoinBalance(
                    coin = coin["coin"]?.asText() ?: "",
                    walletBalance = coin["walletBalance"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    availableBalance = coin["availableToWithdraw"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    unrealisedPnl = coin["unrealisedPnl"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                )
            } ?: emptyList()

        return WalletBalance(
            totalEquity = account["totalEquity"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            totalWalletBalance = account["totalWalletBalance"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            totalAvailableBalance = account["totalAvailableBalance"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            totalUnrealisedPnl = account["totalPerpUPL"]?.asText()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            coins = coins,
        )
    }

    private fun isSuccess(root: JsonNode): Boolean = root["retCode"]?.asInt() == 0
}

/**
 * Configuration for Bybit trading client.
 */
data class BybitTradingConfig(
    val baseUrl: String,
    val httpTimeoutMs: Long = 30000,
)

/**
 * Exception for Bybit API errors.
 */
class BybitApiException(
    val code: Int,
    override val message: String,
) : RuntimeException("Bybit API error $code: $message")
