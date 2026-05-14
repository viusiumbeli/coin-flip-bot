package com.trading.coinflip.exchange.bybit

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Handles Bybit V5 API authentication via HMAC-SHA256 signatures.
 * Single responsibility: generate authentication headers for API requests.
 *
 * Signature format: HMAC_SHA256(timestamp + apiKey + recvWindow + payload, apiSecret)
 * - For POST: payload = JSON body
 * - For GET: payload = query string (without leading ?)
 */
class BybitAuthenticator(
    private val apiKey: String,
    private val apiSecret: String,
) {
    companion object {
        private const val RECV_WINDOW = "5000"
        private const val HMAC_SHA256 = "HmacSHA256"
    }

    /**
     * Generate HMAC-SHA256 signature for the given payload.
     *
     * @param timestamp Current time in milliseconds
     * @param payload Request body (POST) or query string (GET)
     * @return Lowercase hex-encoded signature
     */
    fun sign(
        timestamp: String,
        payload: String,
    ): String {
        val message = timestamp + apiKey + RECV_WINDOW + payload
        return hmacSha256(message, apiSecret)
    }

    /**
     * Generate all required authentication headers.
     *
     * @param payload Request body for POST, query string for GET
     * @return Map of headers to include in the request
     */
    fun generateHeaders(payload: String = ""): Map<String, String> {
        val timestamp = System.currentTimeMillis().toString()
        return mapOf(
            "X-BAPI-API-KEY" to apiKey,
            "X-BAPI-TIMESTAMP" to timestamp,
            "X-BAPI-SIGN" to sign(timestamp, payload),
            "X-BAPI-RECV-WINDOW" to RECV_WINDOW,
        )
    }

    /**
     * Compute HMAC-SHA256 and return as lowercase hex string.
     */
    private fun hmacSha256(
        message: String,
        secret: String,
    ): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        val secretKey = SecretKeySpec(secret.toByteArray(), HMAC_SHA256)
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
