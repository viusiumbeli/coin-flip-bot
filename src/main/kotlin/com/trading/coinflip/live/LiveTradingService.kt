package com.trading.coinflip.live

import com.trading.coinflip.candle.BinanceClient
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.candle.CandleRepository
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.config.LiveProperties
import com.trading.coinflip.common.model.Timeframe
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
    private val binanceClient: BinanceClient,
    private val tradingProcessor: TradingProcessor,
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionJobs = ConcurrentHashMap<String, Job>()
    private val stateHolders = ConcurrentHashMap<String, LiveTradingStateHolder>()
    private val mutex = Mutex()

    private fun sessionKey(
        symbol: String,
        timeframe: Timeframe,
    ): String = "${symbol}_${timeframe.label}"

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
                log.info { "Resuming session for ${session.symbol} ${session.timeframe.label}" }
                resumeSession(session)
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
    suspend fun startSession(
        symbol: String,
        timeframe: Timeframe,
    ): LiveSessionEntity =
        mutex.withLock {
            val key = sessionKey(symbol, timeframe)
            if (sessionJobs.containsKey(key)) {
                throw IllegalStateException("Session already running for $symbol ${timeframe.label}")
            }

            // Create new session in database
            val session =
                sessionRepository.save(
                    LiveSessionEntity(
                        symbol = symbol,
                        timeframe = timeframe,
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
                    timeframe = timeframe,
                    initialState = TradingState.create(liveProperties.initialCapital),
                )
            stateHolders[key] = stateHolder

            // Prefetch historical candles for ATR initialization
            prefetchHistoricalCandles(stateHolder)

            // Start WebSocket streaming
            val job =
                scope.launch {
                    runSession(stateHolder)
                }
            sessionJobs[key] = job

            log.info { "[#${session.id} $symbol/${timeframe.label}] Started live trading session" }
            session
        }

    /**
     * Resume an existing session from database.
     */
    private suspend fun resumeSession(session: LiveSessionEntity) =
        mutex.withLock {
            val key = sessionKey(session.symbol, session.timeframe)

            if (sessionJobs.containsKey(key)) {
                log.warn { "Session already running for ${session.symbol} ${session.timeframe.label}, skipping resume" }
                return
            }

            val stateHolder = recoveryService.recoverState(session)
            stateHolders[key] = stateHolder

            val job =
                scope.launch {
                    runSession(stateHolder)
                }
            sessionJobs[key] = job

            log.info { "[#${session.id} ${session.symbol}/${session.timeframe.label}] Resumed session" }
        }

    /**
     * Stop a running session.
     */
    suspend fun stopSession(
        symbol: String,
        timeframe: Timeframe,
    ) = mutex.withLock {
        val key = sessionKey(symbol, timeframe)
        val job = sessionJobs.remove(key)
        val stateHolder = stateHolders.remove(key)

        if (job == null || stateHolder == null) {
            throw IllegalStateException("No running session for $symbol ${timeframe.label}")
        }

        job.cancel()

        // Update session status in database
        val session = sessionRepository.findById(stateHolder.sessionId)
        if (session != null) {
            session.status = LiveSessionStatus.STOPPED
            session.stoppedAt = Instant.now()
            sessionRepository.save(session)
        }

        log.info { "[#${stateHolder.sessionId} $symbol/${timeframe.label}] Stopped session" }
    }

    /**
     * Main session loop - connects to WebSocket and processes candles.
     */
    private suspend fun runSession(stateHolder: LiveTradingStateHolder) {
        webSocketClient
            .connectAndStream(stateHolder.symbol, stateHolder.timeframe, scope)
            .onEach { rawCandle ->
                processCompletedCandle(stateHolder, rawCandle)
            }.catch { e ->
                log.error(e) { "${stateHolder.logPrefix} Session error" }
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
        log.info { "${stateHolder.logPrefix} Processing candle at ${rawCandle.openTime}" }

        // Save candle to database - ATR is calculated atomically by PostgreSQL trigger
        val savedCandle = persistCandle(rawCandle)

        // Verify ATR was calculated (requires enough historical data)
        if (savedCandle.atr == null) {
            log.warn { "${stateHolder.logPrefix} No ATR calculated (not enough history), skipping candle" }
            stateHolder.updateLastCandle(savedCandle)
            return
        }

        stateHolder.updateLastCandle(savedCandle)

        // Process candle through trading processor
        stateHolder.withState { currentState ->
            val events = tradingProcessor.processCandle(currentState, savedCandle)

            val stateToSave =
                if (events.isNotEmpty()) {
                    val newState = currentState.applyEvents(events)
                    stateHolder.updateState(newState)

                    // Persist events
                    persistEvents(stateHolder, events)

                    for (event in events) {
                        when (event) {
                            is TradingEvent.PositionOpened ->
                                log.info {
                                    "${stateHolder.logPrefix} Opened ${event.position.side} #${event.position.id} at ${event.position.entryPrice}"
                                }
                            is TradingEvent.PositionUpdated ->
                                log.info {
                                    "${stateHolder.logPrefix} Updated trailing stop #${event.positionId} to ${event.newTrailingStop}"
                                }
                            is TradingEvent.PositionClosed ->
                                log.info {
                                    "${stateHolder.logPrefix} Closed #${event.positionId} P&L=${event.pnl} (${event.exitReason})"
                                }
                        }
                    }
                    newState
                } else {
                    log.info { "${stateHolder.logPrefix} Processed candle, no position changes" }
                    currentState
                }

            // Always update session with latest candle ID
            updateSessionFromState(stateHolder, stateToSave, savedCandle)
        }

        // Take balance snapshot if needed
        maybeCreateBalanceSnapshot(stateHolder, savedCandle)
    }

    /**
     * Prefetch historical candles from Binance API to ensure ATR is available.
     */
    private suspend fun prefetchHistoricalCandles(stateHolder: LiveTradingStateHolder) {
        val count = liveProperties.prefetchCandleCount
        log.info { "${stateHolder.logPrefix} Prefetching last $count candles for ATR initialization" }

        // Fetch from Binance REST API
        val candles =
            binanceClient.fetchHistoricalKlines(
                symbol = stateHolder.symbol,
                timeframe = stateHolder.timeframe,
                limit = count,
            )

        if (candles.isEmpty()) {
            log.warn { "${stateHolder.logPrefix} No historical candles fetched from Binance API" }
            return
        }

        // Save to DB (triggers ATR calculation)
        for (candle in candles) {
            persistCandle(candle)
        }

        // Update lastCandle with most recent that has ATR
        val lastWithAtr =
            candleRepository.findLastCandleWithATR(
                stateHolder.symbol,
                stateHolder.timeframe,
            )
        if (lastWithAtr != null) {
            stateHolder.lastCandle = lastWithAtr
            log.info { "${stateHolder.logPrefix} ATR initialized: ${lastWithAtr.atr} at ${lastWithAtr.openTime}" }
        } else {
            log.warn { "${stateHolder.logPrefix} No ATR calculated after prefetch (need more history)" }
        }
    }

    /**
     * Persist trading events to database.
     */
    private suspend fun persistEvents(
        stateHolder: LiveTradingStateHolder,
        events: List<TradingEvent>,
    ) {
        val sessionId = stateHolder.sessionId
        for (event in events) {
            when (event) {
                is TradingEvent.PositionOpened -> {
                    val entity = LivePositionEntity.fromPosition(event.position, sessionId)
                    positionRepository.save(entity)
                    log.info { "${stateHolder.logPrefix} Persisted new position: ${event.position.id} ${event.position.side}" }
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
                        "${stateHolder.logPrefix} Closed position ${event.positionId}: P&L=${event.pnl}, Reason=${event.exitReason}"
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
     * Persist candle to database and return the saved entity with trigger-calculated ATR.
     */
    private suspend fun persistCandle(candle: CandleEntity): CandleEntity =
        try {
            candleRepository.save(candle)
            // Re-fetch to get trigger-calculated ATR (save() returns input entity, not DB-modified)
            candleRepository.findBySymbolAndTimeframeAndOpenTime(
                candle.symbol,
                candle.timeframe,
                candle.openTime,
            ) ?: candle
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
    suspend fun getAllSessionStatus(): List<LiveSessionEntity> = sessionRepository.findAllByOrderByStartedAtDesc().toList()

    /**
     * Get state for a specific symbol and timeframe.
     */
    fun getStateHolder(
        symbol: String,
        timeframe: Timeframe,
    ): LiveTradingStateHolder? = stateHolders[sessionKey(symbol, timeframe)]

    /**
     * Check if a symbol and timeframe has an active session.
     */
    fun isSessionActive(
        symbol: String,
        timeframe: Timeframe,
    ): Boolean = sessionJobs.containsKey(sessionKey(symbol, timeframe))
}
