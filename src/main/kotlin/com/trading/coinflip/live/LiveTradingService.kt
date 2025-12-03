package com.trading.coinflip.live

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.config.LiveProperties
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.data.CandleRepository
import com.trading.coinflip.engine.ATRCalculator
import com.trading.coinflip.engine.TradingProcessor
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.TradingEvent
import com.trading.coinflip.engine.model.TradingState
import com.trading.coinflip.live.model.LiveBalanceSnapshotEntity
import com.trading.coinflip.live.model.LivePositionEntity
import com.trading.coinflip.live.model.LiveSessionEntity
import com.trading.coinflip.live.model.LiveSessionStatus
import com.trading.coinflip.live.model.LiveTradeEntity
import com.trading.coinflip.live.repository.LiveBalanceSnapshotRepository
import com.trading.coinflip.live.repository.LivePositionRepository
import com.trading.coinflip.live.repository.LiveSessionRepository
import com.trading.coinflip.live.repository.LiveTradeRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

@Service
class LiveTradingService(
    private val webSocketClient: BinanceWebSocketClient,
    private val tradingProcessor: TradingProcessor,
    private val atrCalculator: ATRCalculator,
    private val recoveryService: LiveStateRecoveryService,
    private val sessionRepository: LiveSessionRepository,
    private val positionRepository: LivePositionRepository,
    private val tradeRepository: LiveTradeRepository,
    private val balanceSnapshotRepository: LiveBalanceSnapshotRepository,
    private val candleRepository: CandleRepository,
    private val liveProperties: LiveProperties,
    private val backtestProperties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}
    private val tradingConfig = backtestProperties.trading

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionJobs = ConcurrentHashMap<String, Job>()
    private val stateHolders = ConcurrentHashMap<String, LiveTradingStateHolder>()
    private val mutex = Mutex()

    @PostConstruct
    fun initialize() {
        if (!liveProperties.enabled) {
            log.info { "Live trading is disabled" }
            return
        }

        log.info { "Initializing live trading service for symbols: ${liveProperties.symbols}" }

        scope.launch {
            // Resume any running sessions from previous run
            val runningSessions = recoveryService.findRunningSessions()
            for (session in runningSessions) {
                log.info { "Resuming session for ${session.symbol}" }
                resumeSession(session)
            }

            // Start new sessions for configured symbols not already running
            for (symbol in liveProperties.symbols) {
                if (!sessionJobs.containsKey(symbol)) {
                    try {
                        startSession(symbol)
                    } catch (e: Exception) {
                        log.error(e) { "Failed to start session for $symbol" }
                    }
                }
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        log.info { "Shutting down live trading service" }
        webSocketClient.stop()
        scope.cancel()
    }

    /**
     * Start a new live trading session for a symbol.
     */
    suspend fun startSession(symbol: String): LiveSessionEntity =
        mutex.withLock {
            if (sessionJobs.containsKey(symbol)) {
                throw IllegalStateException("Session already running for $symbol")
            }

            // Create new session in database
            val session =
                sessionRepository.save(
                    LiveSessionEntity(
                        symbol = symbol,
                        timeframe = Timeframe.ONE_HOUR,
                        initialCapital = liveProperties.initialCapital,
                        currentBalance = liveProperties.initialCapital,
                        peakBalance = liveProperties.initialCapital,
                    ),
                )

            // Initialize state holder
            val stateHolder =
                LiveTradingStateHolder(
                    sessionId = session.id!!,
                    symbol = symbol,
                    initialState = TradingState.create(liveProperties.initialCapital),
                )
            stateHolders[symbol] = stateHolder

            // Initialize ATR from historical data
            initializeAtrFromHistory(symbol, stateHolder)

            // Start WebSocket streaming
            val job =
                scope.launch {
                    runSession(symbol, stateHolder)
                }
            sessionJobs[symbol] = job

            log.info { "Started live trading session for $symbol (id=${session.id})" }
            session
        }

    /**
     * Resume an existing session from database.
     */
    private suspend fun resumeSession(session: LiveSessionEntity) =
        mutex.withLock {
            val symbol = session.symbol

            if (sessionJobs.containsKey(symbol)) {
                log.warn { "Session already running for $symbol, skipping resume" }
                return
            }

            val stateHolder = recoveryService.recoverState(session)
            stateHolders[symbol] = stateHolder

            val job =
                scope.launch {
                    runSession(symbol, stateHolder)
                }
            sessionJobs[symbol] = job

            log.info { "Resumed session for $symbol (id=${session.id})" }
        }

    /**
     * Stop a running session.
     */
    suspend fun stopSession(symbol: String) =
        mutex.withLock {
            val job = sessionJobs.remove(symbol)
            val stateHolder = stateHolders.remove(symbol)

            if (job == null || stateHolder == null) {
                throw IllegalStateException("No running session for $symbol")
            }

            job.cancel()

            // Update session status in database
            val session = sessionRepository.findById(stateHolder.sessionId)
            if (session != null) {
                session.status = LiveSessionStatus.STOPPED
                session.stoppedAt = Instant.now()
                sessionRepository.save(session)
            }

            log.info { "Stopped session for $symbol" }
        }

    /**
     * Main session loop - connects to WebSocket and processes candles.
     */
    private suspend fun runSession(
        symbol: String,
        stateHolder: LiveTradingStateHolder,
    ) {
        webSocketClient
            .connectAndStream(symbol, Timeframe.ONE_HOUR, scope)
            .onEach { rawCandle ->
                processCompletedCandle(stateHolder, rawCandle)
            }.catch { e ->
                log.error(e) { "Session error for $symbol" }
                handleSessionError(stateHolder, e)
            }.collect()
    }

    /**
     * Process a completed candle from WebSocket.
     */
    private suspend fun processCompletedCandle(
        stateHolder: LiveTradingStateHolder,
        rawCandle: CandleEntity,
    ) {
        val symbol = stateHolder.symbol
        log.info { "Processing candle for $symbol at ${rawCandle.openTime}" }

        // Calculate ATR incrementally
        val previousCandle = stateHolder.lastCandle
        if (previousCandle?.atr == null) {
            log.warn { "No ATR available for $symbol, skipping candle" }
            return
        }

        val candleWithAtr =
            atrCalculator
                .calculateATRIncremental(
                    previousCandle = previousCandle,
                    newCandles = listOf(rawCandle),
                    period = tradingConfig.atrPeriod,
                ).first()

        // Save candle to database first to get ID
        val savedCandle = persistCandle(candleWithAtr)
        stateHolder.updateLastCandle(savedCandle)

        // Process candle through trading processor
        stateHolder.withState { currentState ->
            val events = tradingProcessor.processCandle(currentState, savedCandle)

            val stateToSave =
                if (events.isNotEmpty()) {
                    val newState = currentState.applyEvents(events)
                    stateHolder.updateState(newState)

                    // Persist events
                    persistEvents(stateHolder.sessionId, events)

                    log.info { "Processed ${events.size} events for $symbol" }
                    newState
                } else {
                    currentState
                }

            // Always update session with latest candle ID
            updateSessionFromState(stateHolder, stateToSave, savedCandle)
        }

        // Take balance snapshot if needed
        maybeCreateBalanceSnapshot(stateHolder, savedCandle)
    }

    /**
     * Initialize ATR from historical candle data.
     */
    private suspend fun initializeAtrFromHistory(
        symbol: String,
        stateHolder: LiveTradingStateHolder,
    ) {
        val lastCandle = candleRepository.findLastCandleWithATR(symbol, Timeframe.ONE_HOUR)
        if (lastCandle != null) {
            stateHolder.lastCandle = lastCandle
            log.info { "Initialized ATR from history: ${lastCandle.atr} at ${lastCandle.openTime}" }
        } else {
            log.warn { "No historical ATR found for $symbol, will need to build from stream" }
        }
    }

    /**
     * Persist trading events to database.
     */
    private suspend fun persistEvents(
        sessionId: Long,
        events: List<TradingEvent>,
    ) {
        for (event in events) {
            when (event) {
                is TradingEvent.PositionOpened -> {
                    val entity = LivePositionEntity.fromPosition(event.position, sessionId)
                    positionRepository.save(entity)
                    log.info { "Persisted new position: ${event.position.id} ${event.position.side}" }
                }

                is TradingEvent.PositionUpdated -> {
                    val entity =
                        positionRepository.findBySessionIdAndPositionId(
                            sessionId,
                            event.positionId,
                        )
                    if (entity != null) {
                        entity.trailingStop = event.newTrailingStop
                        entity.highestFavorablePrice = event.newHighestFavorablePrice
                        entity.updatedAt = Instant.now()
                        positionRepository.save(entity)
                    }
                }

                is TradingEvent.PositionClosed -> {
                    // Update position status to closed
                    val entity =
                        positionRepository.findBySessionIdAndPositionId(sessionId, event.positionId)
                    if (entity != null) {
                        entity.status = PositionStatus.CLOSED
                        entity.updatedAt = Instant.now()
                        positionRepository.save(entity)
                    }

                    // Save to trades (source of truth for exit data)
                    val tradeEntity = LiveTradeEntity.fromTrade(event.trade, sessionId)
                    tradeRepository.save(tradeEntity)
                    log.info {
                        "Closed position ${event.positionId}: " +
                                "P&L=${event.pnl}, Reason=${event.exitReason}"
                    }
                }
            }
        }
    }

    /**
     * Update session entity from current trading state.
     */
    private suspend fun updateSessionFromState(
        stateHolder: LiveTradingStateHolder,
        state: TradingState,
        candle: CandleEntity,
    ) {
        val session = sessionRepository.findById(stateHolder.sessionId) ?: return
        session.currentBalance = state.accountBalance
        session.peakBalance = state.peakBalance
        session.maxDrawdown = state.maxDrawdown
        session.positionIdCounter = state.positionIdCounter
        session.tradeIdCounter = state.tradeIdCounter
        session.lastCandleId = candle.id
        session.lastUpdateAt = Instant.now()
        sessionRepository.save(session)
    }

    /**
     * Persist candle to database and return the saved entity with ID.
     */
    private suspend fun persistCandle(candle: CandleEntity): CandleEntity =
        try {
            candleRepository.save(candle)
        } catch (e: Exception) {
            // Likely duplicate - find existing candle
            log.debug { "Candle already exists: ${candle.symbol} ${candle.openTime}" }
            candleRepository.findBySymbolAndTimeframeAndOpenTime(
                candle.symbol,
                candle.timeframe,
                candle.openTime,
            ) ?: candle
        }

    /**
     * Create balance snapshot at configured intervals.
     */
    private suspend fun maybeCreateBalanceSnapshot(
        stateHolder: LiveTradingStateHolder,
        candle: CandleEntity,
    ) {
        // Snapshot every N hours (N = snapshot interval in minutes / 60)
        val snapshotEveryNHours = liveProperties.balanceSnapshotIntervalMinutes / 60
        if (snapshotEveryNHours <= 0) return

        val hourOfDay = candle.openTime.atZone(ZoneOffset.UTC).hour

        if (hourOfDay % snapshotEveryNHours == 0) {
            stateHolder.withState { state ->
                val unrealizedPnl = calculateUnrealizedPnl(state, candle.close)

                balanceSnapshotRepository.save(
                    LiveBalanceSnapshotEntity(
                        sessionId = stateHolder.sessionId,
                        balance = state.accountBalance,
                        openPositionsCount = state.openPositions.size,
                        unrealizedPnl = unrealizedPnl,
                        candleTime = candle.openTime,
                    ),
                )
                log.debug { "Created balance snapshot: ${state.accountBalance}" }
            }
        }
    }

    /**
     * Calculate unrealized P&L for open positions.
     */
    private fun calculateUnrealizedPnl(
        state: TradingState,
        currentPrice: BigDecimal,
    ): BigDecimal =
        state.openPositions
            .map { position ->
                when (position.side) {
                    PositionSide.LONG ->
                        (currentPrice - position.entryPrice) * position.positionSize

                    PositionSide.SHORT ->
                        (position.entryPrice - currentPrice) * position.positionSize
                }
            }.fold(BigDecimal.ZERO) { acc, pnl -> acc + pnl }

    /**
     * Handle session error.
     */
    private suspend fun handleSessionError(
        stateHolder: LiveTradingStateHolder,
        error: Throwable,
    ) {
        val session = sessionRepository.findById(stateHolder.sessionId) ?: return
        session.status = LiveSessionStatus.ERROR
        session.errorMessage = error.message
        session.lastUpdateAt = Instant.now()
        sessionRepository.save(session)
    }

    // --- Public query methods ---

    /**
     * Get current status for all sessions.
     */
    suspend fun getAllSessionStatus(): List<LiveSessionEntity> =
        sessionRepository.findAllByOrderByStartedAtDesc().toList()

    /**
     * Get state for a specific symbol.
     */
    fun getStateHolder(symbol: String): LiveTradingStateHolder? = stateHolders[symbol]

    /**
     * Check if a symbol has an active session.
     */
    fun isSessionActive(symbol: String): Boolean = sessionJobs.containsKey(symbol)
}
