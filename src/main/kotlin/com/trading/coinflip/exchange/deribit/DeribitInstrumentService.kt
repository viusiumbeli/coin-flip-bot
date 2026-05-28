package com.trading.coinflip.exchange.deribit

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import mu.KotlinLogging
import java.time.Instant

/**
 * Service for discovering available Deribit option instruments.
 * Queries the public API for active options by currency.
 */
class DeribitInstrumentService(
    private val objectMapper: ObjectMapper,
    private val baseUrl: String,
) {
    private val log = KotlinLogging.logger {}

    private val client = HttpClient(CIO)

    /**
     * Fetch available option instruments for a given currency (e.g. "ETH", "BTC").
     * Uses Deribit public API: GET /public/get_instruments?currency={}&kind=option
     */
    suspend fun getOptionInstruments(currency: String): List<DeribitInstrument> {
        val url = "$baseUrl/public/get_instruments?currency=${currency.uppercase()}&kind=option"

        return try {
            val response = client.get(url)
            val body = response.bodyAsText()
            val root = objectMapper.readTree(body)

            val result =
                root["result"] ?: run {
                    val error = root["error"]
                    if (error != null) {
                        log.error { "Deribit API error: ${error["message"]?.asText()}" }
                    }
                    return emptyList()
                }

            if (!result.isArray) return emptyList()

            result.mapNotNull { node ->
                try {
                    DeribitInstrument(
                        instrumentName = node["instrument_name"].asText(),
                        baseCurrency = node["base_currency"].asText(),
                        quoteCurrency = node["quote_currency"].asText(),
                        strike = node["strike"].asDouble(),
                        optionType = node["option_type"].asText(),
                        expirationTimestamp = Instant.ofEpochMilli(node["expiration_timestamp"].asLong()),
                        isActive = node["is_active"]?.asBoolean() ?: true,
                    )
                } catch (e: Exception) {
                    log.warn(e) { "Failed to parse instrument: ${node["instrument_name"]?.asText()}" }
                    null
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch Deribit instruments for currency=$currency" }
            emptyList()
        }
    }
}
