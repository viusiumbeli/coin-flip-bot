package com.trading.coinflip.live

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.math.BigDecimal

/**
 * Publishes live trading events via SSE to connected clients.
 * Uses a Sinks.Many with multicast to broadcast to all subscribers.
 */
@Component
class LiveEventPublisher(
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger {}

    // Multicast sink - drops events when no subscribers (expected for SSE)
    private val sink: Sinks.Many<LiveEvent> = Sinks.many().multicast().directBestEffort()

    /**
     * Get event stream for SSE subscription.
     */
    fun getEventStream(): Flux<LiveEvent> = sink.asFlux()

    /**
     * Publish a session update event (balance changed, position opened/closed, etc.)
     */
    fun publishSessionUpdate(
        sessionId: Long,
        symbol: String,
        eventType: LiveEventType,
        data: Map<String, Any?> = emptyMap(),
    ) {
        val event =
            LiveEvent(
                type = eventType,
                sessionId = sessionId,
                symbol = symbol,
                data = data,
                timestamp = System.currentTimeMillis(),
            )

        val result = sink.tryEmitNext(event)
        if (result.isSuccess) {
            log.debug { "Published event: $eventType for session $sessionId" }
        }
        // No logging for failures - expected when no subscribers connected
    }

    /**
     * Publish position opened event.
     */
    fun publishPositionOpened(
        sessionId: Long,
        symbol: String,
        positionId: Long,
        side: String,
        entryPrice: BigDecimal,
        positionSize: BigDecimal,
        trailingStop: BigDecimal,
    ) {
        publishSessionUpdate(
            sessionId = sessionId,
            symbol = symbol,
            eventType = LiveEventType.POSITION_OPENED,
            data =
                mapOf(
                    "positionId" to positionId,
                    "side" to side,
                    "entryPrice" to entryPrice,
                    "positionSize" to positionSize,
                    "trailingStop" to trailingStop,
                ),
        )
    }

    /**
     * Publish position updated event (trailing stop moved).
     */
    fun publishPositionUpdated(
        sessionId: Long,
        symbol: String,
        positionId: Long,
        newTrailingStop: BigDecimal,
    ) {
        publishSessionUpdate(
            sessionId = sessionId,
            symbol = symbol,
            eventType = LiveEventType.POSITION_UPDATED,
            data =
                mapOf(
                    "positionId" to positionId,
                    "newTrailingStop" to newTrailingStop,
                ),
        )
    }

    /**
     * Publish position closed event.
     */
    fun publishPositionClosed(
        sessionId: Long,
        symbol: String,
        positionId: Long,
        pnl: BigDecimal,
        exitReason: String,
        newBalance: BigDecimal,
    ) {
        publishSessionUpdate(
            sessionId = sessionId,
            symbol = symbol,
            eventType = LiveEventType.POSITION_CLOSED,
            data =
                mapOf(
                    "positionId" to positionId,
                    "pnl" to pnl,
                    "exitReason" to exitReason,
                    "newBalance" to newBalance,
                ),
        )
    }

    /**
     * Publish candle processed event (periodic update).
     */
    fun publishCandleProcessed(
        sessionId: Long,
        symbol: String,
        currentBalance: BigDecimal,
        openPositionsCount: Int,
        lastPrice: BigDecimal,
        lastAtr: BigDecimal?,
    ) {
        publishSessionUpdate(
            sessionId = sessionId,
            symbol = symbol,
            eventType = LiveEventType.CANDLE_PROCESSED,
            data =
                mapOf(
                    "currentBalance" to currentBalance,
                    "openPositionsCount" to openPositionsCount,
                    "lastPrice" to lastPrice,
                    "lastAtr" to lastAtr,
                ),
        )
    }

    /**
     * Publish session started event.
     */
    fun publishSessionStarted(
        sessionId: Long,
        symbol: String,
        timeframe: String,
        exchange: String,
    ) {
        publishSessionUpdate(
            sessionId = sessionId,
            symbol = symbol,
            eventType = LiveEventType.SESSION_STARTED,
            data =
                mapOf(
                    "timeframe" to timeframe,
                    "exchange" to exchange,
                ),
        )
    }

    /**
     * Publish session stopped event.
     */
    fun publishSessionStopped(
        sessionId: Long,
        symbol: String,
    ) {
        publishSessionUpdate(
            sessionId = sessionId,
            symbol = symbol,
            eventType = LiveEventType.SESSION_STOPPED,
        )
    }
}

/**
 * Types of live trading events.
 */
enum class LiveEventType {
    SESSION_STARTED,
    SESSION_STOPPED,
    POSITION_OPENED,
    POSITION_UPDATED,
    POSITION_CLOSED,
    CANDLE_PROCESSED,
}

/**
 * Live event data structure for SSE.
 */
data class LiveEvent(
    val type: LiveEventType,
    val sessionId: Long,
    val symbol: String,
    val data: Map<String, Any?>,
    val timestamp: Long,
)
