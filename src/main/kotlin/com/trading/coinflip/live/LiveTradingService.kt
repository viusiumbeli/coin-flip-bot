package com.trading.coinflip.live

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
import com.trading.coinflip.exchange.Exchange
import com.trading.coinflip.exchange.ExchangeClientFactory
import com.trading.coinflip.exchange.ExchangeTradingClient
import com.trading.coinflip.exchange.OrderSide
import com.trading.coinflip.exchange.OrderType
import com.trading.coinflip.exchange.PlaceOrderRequest
import com.trading.coinflip.exchange.PositionIdx
import com.trading.coinflip.exchange.TradingStopRequest
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
    private val exchangeClientFactory: ExchangeClientFactory,
    private val tradingProcessor: TradingProcessor,
    private val recoveryService: LiveStateRecoveryService,
    private val sessionRepository: LiveSessionRepository,
    private val positionRepository: LivePositionRepository,
    private val tradeRepository: LiveTradeRepository,
    private val balanceSnapshotRepository: LiveBalanceSnapshotRepository,
    private val candleRepository: CandleRepository,
    private val liveProperties: LiveProperties,
    private val backtestProperties: BacktestProperties,
    private val eventPublisher: LiveEventPublisher,
) {
    private val log = KotlinLogging.logger {}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionJobs = ConcurrentHashMap<String, Job>()
    private val stateHolders = ConcurrentHashMap<String, LiveTradingStateHolder>()
    private val mutex = Mutex()

    // Get clients from factory for specific exchange (factory handles caching)
    private fun getRestClient(exchange: Exchange) = exchangeClientFactory.getRestClient(exchange)

    private fun getWebSocketClient(exchange: Exchange) = exchangeClientFactory.getWebSocketClient(exchange)

    private fun getTradingClient(exchange: Exchange): ExchangeTradingClient? = exchangeClientFactory.getTradingClient(exchange)

    private fun sessionKey(
        symbol: String,
        timeframe: Timeframe,
        exchange: Exchange,
    ): String = "${exchange}_${symbol}_${timeframe.label}"

    @PostConstruct
    fun initialize() {
        if (!liveProperties.enabled) {
            log.info { "Live trading is disabled" }
            return
        }

        val exchange = exchangeClientFactory.getExchange()
        log.info { "Initializing live trading service for symbols: ${liveProperties.symbols} via $exchange" }

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
        exchangeClientFactory.invalidateAllClients()
        scope.cancel()
    }

    /**
     * Start a new live trading session for a symbol.
     */
    suspend fun startSession(
        symbol: String,
        timeframe: Timeframe,
        exchange: Exchange,
    ): LiveSessionEntity =
        mutex.withLock {
            val key = sessionKey(symbol, timeframe, exchange)
            if (sessionJobs.containsKey(key)) {
                throw IllegalStateException("Session already running for $symbol ${timeframe.label} on $exchange")
            }

            // Create new session in database with specified exchange
            val session =
                sessionRepository.save(
                    LiveSessionEntity(
                        symbol = symbol,
                        timeframe = timeframe,
                        exchange = exchange,
                        initialCapital = liveProperties.initialCapital,
                        currentBalance = liveProperties.initialCapital,
                        peakBalance = liveProperties.initialCapital,
                    ),
                )

            // Initialize state holder with exchange info
            val stateHolder =
                LiveTradingStateHolder(
                    sessionId = session.id!!,
                    symbol = symbol,
                    timeframe = timeframe,
                    exchange = exchange,
                    initialState = TradingState.create(liveProperties.initialCapital),
                )
            stateHolders[key] = stateHolder

            // Switch to Hedge Mode on exchange (required for separate Long/Short positions)
            if (liveProperties.executeRealOrders) {
                val tradingClient = getTradingClient(exchange)
                if (tradingClient != null) {
                    try {
                        tradingClient.switchPositionMode(symbol, hedgeMode = true)
                        log.info { "[#${session.id} $symbol] Hedge Mode enabled" }
                    } catch (e: Exception) {
                        log.warn(e) { "[#${session.id} $symbol] Failed to switch to Hedge Mode - may affect position handling" }
                    }
                }
            }

            // Prefetch historical candles for ATR initialization
            prefetchHistoricalCandles(stateHolder)

            // Start WebSocket streaming
            val job =
                scope.launch {
                    runSession(stateHolder)
                }
            sessionJobs[key] = job

            log.info { "[#${session.id} $symbol/${timeframe.label}] Started live trading session via $exchange" }

            // Publish session started event
            eventPublisher.publishSessionStarted(
                sessionId = session.id!!,
                symbol = symbol,
                timeframe = timeframe.label,
                exchange = exchange.name,
            )

            session
        }

    /**
     * Resume an existing session from database.
     */
    private suspend fun resumeSession(session: LiveSessionEntity) =
        mutex.withLock {
            val key = sessionKey(session.symbol, session.timeframe, session.exchange)

            if (sessionJobs.containsKey(key)) {
                log.warn {
                    "Session already running for ${session.symbol} ${session.timeframe.label} on ${session.exchange}, skipping resume"
                }
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
        exchange: Exchange,
    ) = mutex.withLock {
        val key = sessionKey(symbol, timeframe, exchange)
        val job = sessionJobs.remove(key)
        val stateHolder = stateHolders.remove(key)

        if (job == null || stateHolder == null) {
            throw IllegalStateException("No running session for $symbol ${timeframe.label} on $exchange")
        }

        job.cancel()

        // Update session status in database
        val session = sessionRepository.findById(stateHolder.sessionId)
        if (session != null) {
            session.status = LiveSessionStatus.STOPPED
            session.stoppedAt = Instant.now()
            sessionRepository.save(session)
        }

        log.info { "[#${stateHolder.sessionId} $symbol/${timeframe.label} $exchange] Stopped session" }

        // Publish session stopped event
        eventPublisher.publishSessionStopped(
            sessionId = stateHolder.sessionId,
            symbol = symbol,
        )
    }

    /**
     * Main session loop - connects to WebSocket and processes candles.
     * Uses the session's configured exchange for WebSocket connection.
     */
    private suspend fun runSession(stateHolder: LiveTradingStateHolder) {
        val wsClient = getWebSocketClient(stateHolder.exchange)
        wsClient
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

            // Publish candle processed event for UI updates
            eventPublisher.publishCandleProcessed(
                sessionId = stateHolder.sessionId,
                symbol = stateHolder.symbol,
                currentBalance = stateToSave.accountBalance,
                openPositionsCount = stateToSave.openPositions.size,
                lastPrice = savedCandle.close,
                lastAtr = savedCandle.atr,
            )
        }

        // Take balance snapshot if needed
        maybeCreateBalanceSnapshot(stateHolder, savedCandle)
    }

    /**
     * Prefetch historical candles from exchange API to ensure ATR is available.
     * Uses the session's configured exchange for API calls.
     */
    private suspend fun prefetchHistoricalCandles(stateHolder: LiveTradingStateHolder) {
        val count = liveProperties.prefetchCandleCount
        log.info { "${stateHolder.logPrefix} Prefetching last $count candles for ATR initialization" }

        // Fetch from session's exchange REST API
        val restClient = getRestClient(stateHolder.exchange)
        val candles =
            restClient.fetchHistoricalKlines(
                symbol = stateHolder.symbol,
                timeframe = stateHolder.timeframe,
                limit = count,
            )

        if (candles.isEmpty()) {
            log.warn { "${stateHolder.logPrefix} No historical candles fetched from ${stateHolder.exchange} API" }
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
     * Persist trading events to database and execute real orders if enabled.
     */
    private suspend fun persistEvents(
        stateHolder: LiveTradingStateHolder,
        events: List<TradingEvent>,
    ) {
        val sessionId = stateHolder.sessionId
        val tradingClient = if (liveProperties.executeRealOrders) getTradingClient(stateHolder.exchange) else null

        for (event in events) {
            when (event) {
                is TradingEvent.PositionOpened -> {
                    val entity = LivePositionEntity.fromPosition(event.position, sessionId)
                    positionRepository.save(entity)
                    log.info { "${stateHolder.logPrefix} Persisted new position: ${event.position.id} ${event.position.side}" }

                    // Execute real order on exchange with native trailing stop
                    if (tradingClient != null) {
                        executeOpenPosition(stateHolder, tradingClient, event)
                    }

                    // Publish SSE event
                    eventPublisher.publishPositionOpened(
                        sessionId = sessionId,
                        symbol = stateHolder.symbol,
                        positionId = event.position.id,
                        side = event.position.side.name,
                        entryPrice = event.position.entryPrice,
                        positionSize = event.position.positionSize,
                        trailingStop = event.position.trailingStop,
                    )
                }

                is TradingEvent.PositionUpdated -> {
                    // Update position in database (ByBit handles trailing stop automatically)
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

                        // Publish SSE event
                        eventPublisher.publishPositionUpdated(
                            sessionId = sessionId,
                            symbol = stateHolder.symbol,
                            positionId = event.positionId,
                            newTrailingStop = event.newTrailingStop,
                        )
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

                    // NOTE: We don't close position on exchange - ByBit's trailing stop handles it

                    // Publish SSE event
                    eventPublisher.publishPositionClosed(
                        sessionId = sessionId,
                        symbol = stateHolder.symbol,
                        positionId = event.positionId,
                        pnl = event.pnl,
                        exitReason = event.exitReason,
                        newBalance = event.newBalance,
                    )
                }
            }
        }
    }

    /**
     * Execute real order on exchange when position opens.
     * Places market order first, then sets native trailing stop via ByBit API.
     * ByBit handles trailing automatically - no need to amend on each candle.
     */
    private suspend fun executeOpenPosition(
        stateHolder: LiveTradingStateHolder,
        tradingClient: ExchangeTradingClient,
        event: TradingEvent.PositionOpened,
    ) {
        try {
            val position = event.position
            val orderSide = if (position.side == PositionSide.LONG) OrderSide.Buy else OrderSide.Sell
            val positionIdx = if (position.side == PositionSide.LONG) PositionIdx.HedgeLong else PositionIdx.HedgeShort

            // Calculate trailing stop distance (ATR × multiplier)
            val trailingDistance = (position.entryPrice - position.trailingStop).abs()

            log.info {
                "${stateHolder.logPrefix} [REAL ORDER] Opening ${position.side} qty=${position.positionSize} trailingStop=$trailingDistance"
            }

            // Set leverage first
            tradingClient.setLeverage(stateHolder.symbol, liveProperties.defaultLeverage)

            // 1. Place market order
            val entryResult =
                tradingClient.placeOrder(
                    PlaceOrderRequest(
                        symbol = stateHolder.symbol,
                        side = orderSide,
                        orderType = OrderType.Market,
                        qty = position.positionSize,
                        positionIdx = positionIdx,
                    ),
                )
            log.info { "${stateHolder.logPrefix} [REAL ORDER] Entry order placed: orderId=${entryResult.orderId}" }

            // 2. Set native trailing stop on position (ByBit manages trailing automatically)
            tradingClient.setTradingStop(
                TradingStopRequest(
                    symbol = stateHolder.symbol,
                    positionIdx = positionIdx,
                    trailingStop = trailingDistance,
                ),
            )
            log.info { "${stateHolder.logPrefix} [REAL ORDER] Trailing stop set: distance=$trailingDistance" }
        } catch (e: Exception) {
            log.error(e) { "${stateHolder.logPrefix} [REAL ORDER] Failed to open position on exchange" }
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
     * Get state for a specific symbol, timeframe, and exchange.
     */
    fun getStateHolder(
        symbol: String,
        timeframe: Timeframe,
        exchange: Exchange,
    ): LiveTradingStateHolder? = stateHolders[sessionKey(symbol, timeframe, exchange)]

    /**
     * Check if a symbol, timeframe, and exchange has an active session.
     */
    fun isSessionActive(
        symbol: String,
        timeframe: Timeframe,
        exchange: Exchange,
    ): Boolean = sessionJobs.containsKey(sessionKey(symbol, timeframe, exchange))

    /**
     * Get count of active sessions.
     */
    fun getActiveSessionCount(): Int = sessionJobs.size
}
