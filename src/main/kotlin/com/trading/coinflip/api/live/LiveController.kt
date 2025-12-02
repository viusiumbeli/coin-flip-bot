package com.trading.coinflip.api.live

import com.trading.coinflip.api.exception.NotFoundException
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.live.LiveBalanceSnapshotRepository
import com.trading.coinflip.live.LivePositionRepository
import com.trading.coinflip.live.LiveProperties
import com.trading.coinflip.live.LiveSessionRepository
import com.trading.coinflip.live.LiveTradeRepository
import com.trading.coinflip.live.LiveTradingService
import com.trading.coinflip.live.model.LiveConfigResponse
import com.trading.coinflip.live.model.LiveSessionDetailDto
import com.trading.coinflip.live.model.LiveSessionSummaryDto
import com.trading.coinflip.live.model.LiveSnapshotsResponse
import com.trading.coinflip.live.model.LiveTradesResponse
import com.trading.coinflip.live.toDetailDto
import com.trading.coinflip.live.toDto
import com.trading.coinflip.live.toSummaryDto
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/live")
class LiveController(
    private val liveTradingService: LiveTradingService,
    private val sessionRepository: LiveSessionRepository,
    private val positionRepository: LivePositionRepository,
    private val tradeRepository: LiveTradeRepository,
    private val balanceSnapshotRepository: LiveBalanceSnapshotRepository,
    private val liveProperties: LiveProperties,
) {
    private val log = KotlinLogging.logger {}

    @GetMapping("/config")
    fun getConfig(): LiveConfigResponse =
        LiveConfigResponse(
            enabled = liveProperties.enabled,
            symbols = liveProperties.symbols,
            initialCapital = liveProperties.initialCapital,
            websocketUrl = liveProperties.websocketUrl,
        )

    @GetMapping("/sessions")
    suspend fun getAllSessions(): List<LiveSessionSummaryDto> =
        sessionRepository
            .findAllByOrderByStartedAtDesc()
            .map { it.toSummaryDto() }
            .toList()

    @GetMapping("/sessions/{id}")
    suspend fun getSession(
        @PathVariable id: Long,
    ): LiveSessionDetailDto {
        val session = sessionRepository.findById(id)
            ?: throw NotFoundException("Session not found: $id")

        val currentPrice = session.lastCandleClose

        val positions = positionRepository
            .findBySessionIdAndStatus(id, PositionStatus.OPEN)
            .map { it.toDto(currentPrice) }
            .toList()

        val tradesCount = tradeRepository.countBySessionId(id)

        return session.toDetailDto(positions, tradesCount)
    }

    @GetMapping("/sessions/{id}/trades")
    suspend fun getSessionTrades(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "50") limit: Int,
    ): LiveTradesResponse {
        val session = sessionRepository.findById(id)
            ?: throw NotFoundException("Session not found: $id")

        val trades = tradeRepository
            .findRecentBySessionId(session.id!!, limit.coerceAtMost(100))
            .map { it.toDto() }
            .toList()

        val totalCount = tradeRepository.countBySessionId(id)

        return LiveTradesResponse(trades = trades, totalCount = totalCount)
    }

    @GetMapping("/sessions/{id}/snapshots")
    suspend fun getSessionSnapshots(
        @PathVariable id: Long,
    ): LiveSnapshotsResponse {
        sessionRepository.findById(id)
            ?: throw NotFoundException("Session not found: $id")

        val snapshots = balanceSnapshotRepository
            .findBySessionIdOrderByCandleTimeAsc(id)
            .map { it.toDto() }
            .toList()

        return LiveSnapshotsResponse(snapshots = snapshots)
    }

    @PostMapping("/sessions/{symbol}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    suspend fun startSession(
        @PathVariable symbol: String,
    ): LiveSessionSummaryDto {
        log.info { "Starting live trading session for $symbol" }

        if (!liveProperties.enabled) {
            throw IllegalStateException("Live trading is disabled")
        }

        if (symbol !in liveProperties.symbols) {
            throw IllegalArgumentException("Symbol $symbol is not in configured symbols: ${liveProperties.symbols}")
        }

        val session = liveTradingService.startSession(symbol)
        return session.toSummaryDto()
    }

    @PostMapping("/sessions/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    suspend fun stopSession(
        @PathVariable id: Long,
    ) {
        val session = sessionRepository.findById(id)
            ?: throw NotFoundException("Session not found: $id")

        log.info { "Stopping live trading session for ${session.symbol}" }

        liveTradingService.stopSession(session.symbol)
    }
}
