package com.trading.coinflip.live

import com.trading.coinflip.data.CandleRepository
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.TradingState
import com.trading.coinflip.live.model.LiveSessionEntity
import com.trading.coinflip.live.model.LiveSessionStatus
import com.trading.coinflip.live.repository.LivePositionRepository
import com.trading.coinflip.live.repository.LiveSessionRepository
import com.trading.coinflip.live.repository.LiveTradeRepository
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class LiveStateRecoveryService(
    private val sessionRepository: LiveSessionRepository,
    private val positionRepository: LivePositionRepository,
    private val tradeRepository: LiveTradeRepository,
    private val candleRepository: CandleRepository,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Recover trading state from a persisted session.
     */
    suspend fun recoverState(session: LiveSessionEntity): LiveTradingStateHolder {
        log.info { "Recovering state for session ${session.id} (${session.symbol})" }

        // Load open positions
        val openPositions =
            positionRepository
                .findBySessionIdAndStatus(session.id!!, PositionStatus.OPEN)
                .toList()
                .map { it.toPosition() }

        // Load closed trades count (for logging, not needed for state)
        val tradeCount = tradeRepository.countBySessionId(session.id)

        val tradingState =
            TradingState(
                accountBalance = session.currentBalance,
                peakBalance = session.peakBalance,
                maxDrawdown = session.maxDrawdown,
                openPositions = openPositions,
                closedTrades = emptyList(), // Don't load all trades into memory
                tradeIdCounter = session.tradeIdCounter,
                positionIdCounter = session.positionIdCounter,
            )

        // Load last candle by ID
        val lastCandle = session.lastCandleId?.let { candleRepository.findById(it) }

        log.info {
            "Recovered state: balance=${session.currentBalance}, " +
                "openPositions=${openPositions.size}, " +
                "closedTrades=$tradeCount"
        }

        return LiveTradingStateHolder(
            sessionId = session.id,
            symbol = session.symbol,
            timeframe = session.timeframe,
            initialState = tradingState,
            lastCandle = lastCandle,
        )
    }

    /**
     * Find any running sessions that need to be resumed.
     */
    suspend fun findRunningSessions(): List<LiveSessionEntity> = sessionRepository.findByStatus(LiveSessionStatus.RUNNING).toList()
}
