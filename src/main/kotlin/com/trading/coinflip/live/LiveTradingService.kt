package com.trading.coinflip.live

import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.candle.CandleRepository
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.config.LiveProperties
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.common.model.TrailingStopMode
import com.trading.coinflip.engine.TradingProcessor
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.TradingEvent
import com.trading.coinflip.engine.model.TradingState
import com.trading.coinflip.exchange.Exchange
import com.trading.coinflip.exchange.ExchangeClientFactory
import com.trading.coinflip.exchange.ExchangeTradingClient
import com.trading.coinflip.exchange.ExecutionEvent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight context for a running session.
 * Only holds metadata - state is loaded from DB on each candle.
 */
data class SessionContext(
    val sessionId: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val exchange: Exchange,
    val trailingStopMode: TrailingStopMode,
    val trailingStopPercent: BigDecimal,
    val atrMultiplier: BigDecimal,
    val leverage: Int,
) {
    val logPrefix: String get() = "[#$sessionId $symbol/${timeframe.label} $exchange]"
}

@Service
class LiveTradingService(
    private val exchangeClientFactory: ExchangeClientFactory,
    private val tradingProcessor: TradingProcessor,
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
    private val executionJobs = ConcurrentHashMap<Exchange, Job>() // One per exchange
    private val syncJobs = ConcurrentHashMap<String, Job>() // Periodic position sync per session
    private val sessionContexts = ConcurrentHashMap<String, SessionContext>() // Lightweight metadata only
    private val mutex = Mutex()

    companion object {
        private const val SYNC_INTERVAL_MS = 60_000L // 1 minute
    }

    // Get clients from factory for specific exchange (factory handles caching)
    private fun getRestClient(exchange: Exchange) = exchangeClientFactory.getRestClient(exchange)

    private fun getWebSocketClient(exchange: Exchange) = exchangeClientFactory.getWebSocketClient(exchange)

    private fun getTradingClient(exchange: Exchange): ExchangeTradingClient? = exchangeClientFactory.getTradingClient(exchange)

    private fun getExecutionClient(exchange: Exchange) = exchangeClientFactory.getExecutionClient(exchange)

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
            val runningSessions =
                sessionRepository
                    .findByStatus(LiveSessionStatus.RUNNING)
                    .toList()
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
        trailingStopMode: TrailingStopMode = TrailingStopMode.ATR,
        trailingStopPercent: BigDecimal = BigDecimal("1.0"),
        atrMultiplier: BigDecimal = BigDecimal("3.0"),
        leverage: Int = 1,
    ): LiveSessionEntity =
        mutex.withLock {
            val key = sessionKey(symbol, timeframe, exchange)
            if (sessionJobs.containsKey(key)) {
                throw IllegalStateException("Session already running for $symbol ${timeframe.label} on $exchange")
            }

            // Create new session in database with specified exchange and trailing stop config
            val session =
                sessionRepository.save(
                    LiveSessionEntity(
                        symbol = symbol,
                        timeframe = timeframe,
                        exchange = exchange,
                        initialCapital = liveProperties.initialCapital,
                        currentBalance = liveProperties.initialCapital,
                        peakBalance = liveProperties.initialCapital,
                        trailingStopMode = trailingStopMode,
                        trailingStopPercent = trailingStopPercent,
                        atrMultiplier = atrMultiplier,
                        leverage = leverage,
                    ),
                )

            // Create lightweight context (no state - loaded from DB on each candle)
            val ctx =
                SessionContext(
                    sessionId = session.id!!,
                    symbol = symbol,
                    timeframe = timeframe,
                    exchange = exchange,
                    trailingStopMode = trailingStopMode,
                    trailingStopPercent = trailingStopPercent,
                    atrMultiplier = atrMultiplier,
                    leverage = leverage,
                )
            sessionContexts[key] = ctx

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
            prefetchHistoricalCandles(ctx)

            // Start execution WebSocket if not already running for this exchange
            startExecutionStream(exchange)

            // Start periodic position sync (every minute)
            startPositionSyncJob(key, ctx)

            // Start WebSocket streaming
            val job =
                scope.launch {
                    runSession(ctx)
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

            // Create lightweight context from session entity
            val ctx =
                SessionContext(
                    sessionId = session.id!!,
                    symbol = session.symbol,
                    timeframe = session.timeframe,
                    exchange = session.exchange,
                    trailingStopMode = session.trailingStopMode,
                    trailingStopPercent = session.trailingStopPercent,
                    atrMultiplier = session.atrMultiplier,
                    leverage = session.leverage,
                )
            sessionContexts[key] = ctx

            // Start execution WebSocket if not already running for this exchange
            startExecutionStream(session.exchange)

            // Start periodic position sync (every minute)
            startPositionSyncJob(key, ctx)

            val job =
                scope.launch {
                    runSession(ctx)
                }
            sessionJobs[key] = job

            log.info { "[#${session.id} ${session.symbol}/${session.timeframe.label}] Resumed session" }
        }

    /**
     * Stop a session by ID. Works for both in-memory and orphaned (DB-only) sessions.
     */
    suspend fun stopSessionById(sessionId: Long) =
        mutex.withLock {
            val session =
                sessionRepository.findById(sessionId)
                    ?: throw IllegalStateException("Session not found: $sessionId")

            val key = sessionKey(session.symbol, session.timeframe, session.exchange)

            // Cancel jobs if they exist in memory
            sessionJobs.remove(key)?.cancel()
            syncJobs.remove(key)?.cancel()
            sessionContexts.remove(key)

            // Update session status in database
            session.status = LiveSessionStatus.STOPPED
            session.stoppedAt = Instant.now()
            sessionRepository.save(session)

            log.info { "[#$sessionId ${session.symbol}/${session.timeframe.label} ${session.exchange}] Stopped session" }

            // Publish session stopped event
            eventPublisher.publishSessionStopped(
                sessionId = sessionId,
                symbol = session.symbol,
            )

            // Stop execution stream if no more sessions for this exchange
            val hasSessionsForExchange = sessionContexts.values.any { it.exchange == session.exchange }
            if (!hasSessionsForExchange) {
                stopExecutionStream(session.exchange)
            }
        }

    /**
     * Load TradingState from database for a session.
     * Called on every candle to ensure stateless operation.
     */
    private suspend fun loadStateFromDb(sessionId: Long): TradingState {
        val session =
            sessionRepository.findById(sessionId)
                ?: throw IllegalStateException("Session not found: $sessionId")

        val openPositions =
            positionRepository
                .findBySessionIdAndStatus(sessionId, PositionStatus.OPEN)
                .map { it.toPosition() }
                .toList()

        return TradingState(
            accountBalance = session.currentBalance,
            peakBalance = session.peakBalance,
            maxDrawdown = session.maxDrawdown,
            openPositions = openPositions,
            closedTrades = emptyList(), // Not needed for live processing
            tradeIdCounter = session.tradeIdCounter,
            positionIdCounter = session.positionIdCounter,
        )
    }

    /**
     * Save TradingState to database for a session.
     * Called after each candle is processed.
     */
    private suspend fun saveStateToDb(
        sessionId: Long,
        state: TradingState,
        candle: CandleEntity,
    ) {
        val session =
            sessionRepository.findById(sessionId)
                ?: throw IllegalStateException("Session not found: $sessionId")

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
     * Start execution WebSocket stream for an exchange (one per exchange, shared by all sessions).
     * Receives position close events from exchange when trailing stop triggers.
     */
    private fun startExecutionStream(exchange: Exchange) {
        if (executionJobs.containsKey(exchange)) {
            log.debug { "Execution stream already running for $exchange" }
            return
        }

        if (!liveProperties.executeRealOrders) {
            log.info { "Execution stream disabled (executeRealOrders=false)" }
            return
        }

        val executionClient = getExecutionClient(exchange)
        if (executionClient == null) {
            log.warn { "Execution client not available for $exchange - WebSocket sync disabled" }
            return
        }

        log.info { "Creating execution stream job for $exchange" }
        val job =
            scope.launch {
                log.info { "Connecting to private WebSocket for $exchange execution events..." }
                executionClient
                    .connectAndStream(scope)
                    .onEach { event ->
                        log.info { "Received execution event from $exchange: $event" }
                        handleExecutionEvent(exchange, event)
                    }.catch { e -> log.error(e) { "Execution stream error for $exchange" } }
                    .collect()
                log.warn { "Execution stream for $exchange ended" }
            }
        executionJobs[exchange] = job
        log.info { "Execution stream job started for $exchange" }
    }

    /**
     * Stop execution WebSocket stream for an exchange.
     */
    private fun stopExecutionStream(exchange: Exchange) {
        val job = executionJobs.remove(exchange)
        if (job != null) {
            job.cancel()
            getExecutionClient(exchange)?.stop()
            log.info { "Stopped execution stream for $exchange" }
        }
    }

    /**
     * Start periodic position sync job for a session.
     * Polls exchange every minute to detect positions closed outside WebSocket events.
     */
    private fun startPositionSyncJob(
        key: String,
        ctx: SessionContext,
    ) {
        if (!liveProperties.executeRealOrders) {
            log.info { "${ctx.logPrefix} Position sync disabled (executeRealOrders=false)" }
            return
        }

        val tradingClient = getTradingClient(ctx.exchange)
        if (tradingClient == null) {
            log.warn { "${ctx.logPrefix} Trading client not available - position sync disabled" }
            return
        }

        log.info { "${ctx.logPrefix} Creating position sync job..." }
        val job =
            scope.launch {
                log.info { "${ctx.logPrefix} Position sync job running, first check in ${SYNC_INTERVAL_MS / 1000}s" }
                while (isActive) {
                    delay(SYNC_INTERVAL_MS)
                    try {
                        log.debug { "${ctx.logPrefix} Running position sync check..." }
                        syncPositionsWithExchange(ctx, tradingClient)
                    } catch (e: Exception) {
                        log.warn(e) { "${ctx.logPrefix} Position sync failed" }
                    }
                }
            }
        syncJobs[key] = job
        log.info { "${ctx.logPrefix} Position sync job started (every ${SYNC_INTERVAL_MS / 1000}s)" }
    }

    /**
     * Sync local positions with exchange state.
     * Detects positions that were closed on exchange but not synced locally.
     */
    private suspend fun syncPositionsWithExchange(
        ctx: SessionContext,
        tradingClient: ExchangeTradingClient,
    ) {
        // Load current state from DB
        val currentState = loadStateFromDb(ctx.sessionId)

        if (currentState.openPositions.isEmpty()) {
            return // Nothing to sync
        }

        // Get positions from exchange
        val exchangePositions = tradingClient.getPositions(ctx.symbol)

        for (localPosition in currentState.openPositions) {
            // Find matching exchange position by side
            val exchangePosition =
                exchangePositions.find {
                    it.symbol == ctx.symbol &&
                        (
                            (localPosition.side == PositionSide.LONG && it.side == "Buy") ||
                                (localPosition.side == PositionSide.SHORT && it.side == "Sell")
                        )
                }

            // Check if position is closed on exchange (not found or size = 0)
            val isClosedOnExchange = exchangePosition == null || exchangePosition.size == BigDecimal.ZERO

            if (isClosedOnExchange) {
                log.warn {
                    "${ctx.logPrefix} Position #${localPosition.id} ${localPosition.side} not found on exchange - syncing closure"
                }

                // Close position locally (we don't have P&L from exchange, use 0)
                syncPositionClosureFromPolling(ctx, localPosition)
            }
        }
    }

    /**
     * Sync position closure detected via polling (fallback when WebSocket misses event).
     */
    private suspend fun syncPositionClosureFromPolling(
        ctx: SessionContext,
        position: com.trading.coinflip.engine.model.Position,
    ) {
        // Load current state from DB
        val currentState = loadStateFromDb(ctx.sessionId)

        // Already closed?
        if (currentState.openPositions.none { it.id == position.id }) {
            return
        }

        log.info { "${ctx.logPrefix} Syncing position #${position.id} closure from polling" }

        // Update position in database
        val entity = positionRepository.findBySessionIdAndPositionId(ctx.sessionId, position.id)
        if (entity != null) {
            entity.status = PositionStatus.CLOSED
            entity.updatedAt = Instant.now()
            positionRepository.save(entity)
        }

        // Create trade record (P&L unknown from polling, use trailing stop price estimate)
        val exitPrice = position.trailingStop
        val exitTime = Instant.now()
        val positionValue = position.entryPrice * position.positionSize

        // Calculate raw P&L based on position side
        val rawPnl =
            when (position.side) {
                PositionSide.LONG -> (exitPrice - position.entryPrice) * position.positionSize
                PositionSide.SHORT -> (position.entryPrice - exitPrice) * position.positionSize
            }

        // Deduct transaction costs (entry + exit) to match exchange-reported P&L
        val transactionCostPercent = backtestProperties.trading.transactionCostPercent
        val transactionCost = positionValue * (transactionCostPercent / BigDecimal(100)) * BigDecimal(2)
        val estimatedPnl = rawPnl - transactionCost

        val balanceBeforeClose = currentState.accountBalance
        val balanceAfterClose = currentState.accountBalance + estimatedPnl
        val profitLossPercent =
            if (positionValue > BigDecimal.ZERO) {
                estimatedPnl
                    .divide(positionValue, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
            } else {
                BigDecimal.ZERO
            }

        val newTradeId = currentState.tradeIdCounter + 1
        val trade =
            LiveTradeEntity(
                sessionId = ctx.sessionId,
                tradeId = newTradeId,
                symbol = ctx.symbol,
                timeframe = ctx.timeframe,
                side = position.side,
                entryTime = position.entryTime,
                entryPrice = position.entryPrice,
                exitTime = exitTime,
                exitPrice = exitPrice,
                positionSize = position.positionSize,
                initialStopLoss = position.initialStopLoss,
                trailingStop = position.trailingStop,
                profitLoss = estimatedPnl,
                profitLossPercent = profitLossPercent,
                exitReason = "Trailing stop (polling sync)",
                balanceBeforeOpen = position.balanceBeforeOpen,
                balanceAfterOpen = position.balanceAfterOpen,
                balanceBeforeClose = balanceBeforeClose,
                balanceAfterClose = balanceAfterClose,
            )

        // Try to save trade - may fail if another path already saved it
        val tradeWasSaved =
            try {
                tradeRepository.save(trade)
                true
            } catch (e: DuplicateKeyException) {
                log.info { "${ctx.logPrefix} Trade for position #${position.id} already synced by another path" }
                false
            }

        // Update session in DB
        val newBalance = balanceAfterClose
        val newPeak = maxOf(currentState.peakBalance, newBalance)
        val drawdown =
            if (newPeak > BigDecimal.ZERO) {
                (newPeak - newBalance).divide(newPeak, 8, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
        val newMaxDrawdown = maxOf(currentState.maxDrawdown, drawdown)

        val session = sessionRepository.findById(ctx.sessionId)
        if (session != null) {
            session.currentBalance = newBalance
            session.peakBalance = newPeak
            session.maxDrawdown = newMaxDrawdown
            if (tradeWasSaved) session.tradeIdCounter = newTradeId
            session.lastUpdateAt = Instant.now()
            sessionRepository.save(session)
        }

        // Publish SSE event only if we actually saved the trade
        if (tradeWasSaved) {
            eventPublisher.publishPositionClosed(
                sessionId = ctx.sessionId,
                symbol = ctx.symbol,
                positionId = position.id,
                pnl = estimatedPnl,
                exitReason = "Trailing stop (polling sync)",
                newBalance = newBalance,
            )
        }
    }

    /**
     * Handle execution events from exchange.
     * Updates DB directly when position is closed by exchange (trailing stop triggered).
     */
    private suspend fun handleExecutionEvent(
        exchange: Exchange,
        event: ExecutionEvent,
    ) {
        when (event) {
            is ExecutionEvent.PositionClosed -> {
                log.info {
                    "[$exchange] Position closed by exchange: ${event.symbol} ${event.side} qty=${event.closedSize} pnl=${event.execPnl}"
                }

                // Find matching session context for this symbol
                val ctx =
                    sessionContexts.values.find {
                        it.exchange == exchange && it.symbol == event.symbol
                    }

                if (ctx == null) {
                    log.debug { "No active session for ${event.symbol} on $exchange" }
                    return
                }

                // Sync position closure with DB
                syncPositionClosure(ctx, event)
            }

            is ExecutionEvent.PositionUpdate -> {
                // Log position updates for debugging
                if (event.size == BigDecimal.ZERO) {
                    log.info { "[$exchange] Position fully closed: ${event.symbol}" }
                }
            }
        }
    }

    /**
     * Sync position closure from exchange with DB.
     * Called when trailing stop triggers on exchange side.
     */
    private suspend fun syncPositionClosure(
        ctx: SessionContext,
        event: ExecutionEvent.PositionClosed,
    ) {
        // Determine position side from execution event
        // If exchange closed with Sell, our position was Long (and vice versa)
        val positionSide = if (event.side == "Sell") PositionSide.LONG else PositionSide.SHORT

        // Load current state from DB
        val currentState = loadStateFromDb(ctx.sessionId)

        // Find matching open position
        val position = currentState.openPositions.find { it.side == positionSide }
        if (position == null) {
            log.warn { "${ctx.logPrefix} No open $positionSide position to sync with exchange close" }
            return
        }

        log.info { "${ctx.logPrefix} Syncing position #${position.id} closure from exchange (pnl=${event.execPnl})" }

        // Update position in database
        val entity = positionRepository.findBySessionIdAndPositionId(ctx.sessionId, position.id)
        if (entity != null) {
            entity.status = PositionStatus.CLOSED
            entity.updatedAt = Instant.now()
            positionRepository.save(entity)
        }

        // Create trade record with exchange data
        val exitTime = Instant.now()
        val balanceBeforeClose = currentState.accountBalance
        val balanceAfterClose = currentState.accountBalance + event.execPnl
        val positionValue = position.entryPrice * event.closedSize
        val profitLossPercent =
            if (positionValue > BigDecimal.ZERO) {
                event.execPnl
                    .divide(positionValue, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
            } else {
                BigDecimal.ZERO
            }

        val newTradeId = currentState.tradeIdCounter + 1
        val trade =
            LiveTradeEntity(
                sessionId = ctx.sessionId,
                tradeId = newTradeId,
                symbol = event.symbol,
                timeframe = ctx.timeframe,
                side = positionSide,
                entryTime = position.entryTime,
                entryPrice = position.entryPrice,
                exitTime = exitTime,
                exitPrice = event.execPrice,
                positionSize = event.closedSize,
                initialStopLoss = position.initialStopLoss,
                trailingStop = position.trailingStop,
                profitLoss = event.execPnl,
                profitLossPercent = profitLossPercent,
                exitReason = "Trailing stop (exchange)",
                balanceBeforeOpen = position.balanceBeforeOpen,
                balanceAfterOpen = position.balanceAfterOpen,
                balanceBeforeClose = balanceBeforeClose,
                balanceAfterClose = balanceAfterClose,
            )

        // Try to save trade - may fail if another path already saved it
        val tradeWasSaved =
            try {
                tradeRepository.save(trade)
                true
            } catch (e: DuplicateKeyException) {
                log.info { "${ctx.logPrefix} Trade for position #${position.id} already synced by another path" }
                false
            }

        // Update session in DB
        val newBalance = currentState.accountBalance + event.execPnl
        val newPeak = maxOf(currentState.peakBalance, newBalance)
        val drawdown =
            if (newPeak > BigDecimal.ZERO) {
                (newPeak - newBalance).divide(newPeak, 8, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
        val newMaxDrawdown = maxOf(currentState.maxDrawdown, drawdown)

        val session = sessionRepository.findById(ctx.sessionId)
        if (session != null) {
            session.currentBalance = newBalance
            session.peakBalance = newPeak
            session.maxDrawdown = newMaxDrawdown
            if (tradeWasSaved) session.tradeIdCounter = newTradeId
            session.lastUpdateAt = Instant.now()
            sessionRepository.save(session)
        }

        // Publish SSE event only if we actually saved the trade
        if (tradeWasSaved) {
            eventPublisher.publishPositionClosed(
                sessionId = ctx.sessionId,
                symbol = ctx.symbol,
                positionId = position.id,
                pnl = event.execPnl,
                exitReason = "Trailing stop (exchange)",
                newBalance = newBalance,
            )
        }
    }

    /**
     * Main session loop - connects to WebSocket and processes candles.
     * Uses the session's configured exchange for WebSocket connection.
     */
    private suspend fun runSession(ctx: SessionContext) {
        val wsClient = getWebSocketClient(ctx.exchange)
        wsClient
            .connectAndStream(ctx.symbol, ctx.timeframe, scope)
            .onEach { rawCandle ->
                processCompletedCandle(ctx, rawCandle)
            }.catch { e ->
                log.error(e) { "${ctx.logPrefix} Session error" }
                handleSessionError(ctx, e)
            }.collect()
    }

    /**
     * Process a completed candle from WebSocket.
     * Loads state from DB, processes, saves state back to DB.
     */
    private suspend fun processCompletedCandle(
        ctx: SessionContext,
        rawCandle: CandleEntity,
    ) {
        log.info { "${ctx.logPrefix} Processing candle at ${rawCandle.openTime}" }

        // Save candle to database - ATR is calculated atomically by PostgreSQL trigger
        val savedCandle = persistCandle(rawCandle)

        // Verify ATR was calculated (requires enough historical data)
        if (savedCandle.atr == null) {
            log.warn { "${ctx.logPrefix} No ATR calculated (not enough history), skipping candle" }
            return
        }

        // Load current state from DB
        val currentState = loadStateFromDb(ctx.sessionId)

        // Process candle through trading processor with session-specific config
        val events =
            tradingProcessor.processCandle(
                state = currentState,
                candle = savedCandle,
                trailingStopMode = ctx.trailingStopMode,
                atrMultiplier = ctx.atrMultiplier,
                trailingStopPercent = ctx.trailingStopPercent,
                leverage = ctx.leverage,
            )

        val stateToSave =
            if (events.isNotEmpty()) {
                val newState = currentState.applyEvents(events)

                // Persist events
                persistEvents(ctx, events)

                for (event in events) {
                    when (event) {
                        is TradingEvent.PositionOpened ->
                            log.info {
                                "${ctx.logPrefix} Opened ${event.position.side} #${event.position.id} at ${event.position.entryPrice}"
                            }
                        is TradingEvent.PositionUpdated ->
                            log.info {
                                "${ctx.logPrefix} Updated trailing stop #${event.positionId} to ${event.newTrailingStop}"
                            }
                        is TradingEvent.PositionClosed ->
                            log.info {
                                "${ctx.logPrefix} Closed #${event.positionId} P&L=${event.pnl} (${event.exitReason})"
                            }
                    }
                }
                newState
            } else {
                log.info { "${ctx.logPrefix} Processed candle, no position changes" }
                currentState
            }

        // Save state to DB
        saveStateToDb(ctx.sessionId, stateToSave, savedCandle)

        // Publish candle processed event for UI updates
        eventPublisher.publishCandleProcessed(
            sessionId = ctx.sessionId,
            symbol = ctx.symbol,
            currentBalance = stateToSave.accountBalance,
            openPositionsCount = stateToSave.openPositions.size,
            lastPrice = savedCandle.close,
            lastAtr = savedCandle.atr,
        )

        // Take balance snapshot if needed
        maybeCreateBalanceSnapshot(ctx, stateToSave, savedCandle)
    }

    /**
     * Prefetch historical candles from exchange API to ensure ATR is available.
     * Uses the session's configured exchange for API calls.
     */
    private suspend fun prefetchHistoricalCandles(ctx: SessionContext) {
        val count = liveProperties.prefetchCandleCount
        log.info { "${ctx.logPrefix} Prefetching last $count candles for ATR initialization" }

        // Fetch from session's exchange REST API
        val restClient = getRestClient(ctx.exchange)
        val candles =
            restClient.fetchHistoricalKlines(
                symbol = ctx.symbol,
                timeframe = ctx.timeframe,
                limit = count,
            )

        if (candles.isEmpty()) {
            log.warn { "${ctx.logPrefix} No historical candles fetched from ${ctx.exchange} API" }
            return
        }

        // Save to DB (triggers ATR calculation)
        for (candle in candles) {
            persistCandle(candle)
        }

        // Check if ATR is now available
        val lastWithAtr =
            candleRepository.findLastCandleWithATR(
                ctx.symbol,
                ctx.timeframe,
            )
        if (lastWithAtr != null) {
            log.info { "${ctx.logPrefix} ATR initialized: ${lastWithAtr.atr} at ${lastWithAtr.openTime}" }
        } else {
            log.warn { "${ctx.logPrefix} No ATR calculated after prefetch (need more history)" }
        }
    }

    /**
     * Persist trading events to database and execute real orders if enabled.
     */
    private suspend fun persistEvents(
        ctx: SessionContext,
        events: List<TradingEvent>,
    ) {
        val sessionId = ctx.sessionId
        val tradingClient = if (liveProperties.executeRealOrders) getTradingClient(ctx.exchange) else null

        for (event in events) {
            when (event) {
                is TradingEvent.PositionOpened -> {
                    val entity = LivePositionEntity.fromPosition(event.position, sessionId)
                    positionRepository.save(entity)
                    log.info { "${ctx.logPrefix} Persisted new position: ${event.position.id} ${event.position.side}" }

                    // Execute real order on exchange with native trailing stop
                    if (tradingClient != null) {
                        executeOpenPosition(ctx, tradingClient, event)
                    }

                    // Publish SSE event
                    eventPublisher.publishPositionOpened(
                        sessionId = sessionId,
                        symbol = ctx.symbol,
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
                            symbol = ctx.symbol,
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
                        "${ctx.logPrefix} Closed position ${event.positionId}: P&L=${event.pnl}, Reason=${event.exitReason}"
                    }

                    // NOTE: We don't close position on exchange - ByBit's trailing stop handles it

                    // Publish SSE event
                    eventPublisher.publishPositionClosed(
                        sessionId = sessionId,
                        symbol = ctx.symbol,
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
        ctx: SessionContext,
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
                "${ctx.logPrefix} [REAL ORDER] Opening ${position.side} qty=${position.positionSize} trailingStop=$trailingDistance"
            }

            // Set leverage based on session configuration
            tradingClient.setLeverage(ctx.symbol, ctx.leverage)

            // 1. Place market order
            val entryResult =
                tradingClient.placeOrder(
                    PlaceOrderRequest(
                        symbol = ctx.symbol,
                        side = orderSide,
                        orderType = OrderType.Market,
                        qty = position.positionSize,
                        positionIdx = positionIdx,
                    ),
                )
            log.info { "${ctx.logPrefix} [REAL ORDER] Entry order placed: orderId=${entryResult.orderId}" }

            // 2. Set native trailing stop on position (ByBit manages trailing automatically)
            tradingClient.setTradingStop(
                TradingStopRequest(
                    symbol = ctx.symbol,
                    positionIdx = positionIdx,
                    trailingStop = trailingDistance,
                ),
            )
            log.info { "${ctx.logPrefix} [REAL ORDER] Trailing stop set: distance=$trailingDistance" }
        } catch (e: Exception) {
            log.error(e) { "${ctx.logPrefix} [REAL ORDER] Failed to open position on exchange" }
        }
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
        ctx: SessionContext,
        state: TradingState,
        candle: CandleEntity,
    ) {
        // Snapshot every N hours (N = snapshot interval in minutes / 60)
        val snapshotEveryNHours = liveProperties.balanceSnapshotIntervalMinutes / 60
        if (snapshotEveryNHours <= 0) return

        val hourOfDay = candle.openTime.atZone(ZoneOffset.UTC).hour

        if (hourOfDay % snapshotEveryNHours == 0) {
            val unrealizedPnl = calculateUnrealizedPnl(state, candle.close)

            balanceSnapshotRepository.save(
                LiveBalanceSnapshotEntity(
                    sessionId = ctx.sessionId,
                    balance = state.accountBalance,
                    openPositionsCount = state.openPositions.size,
                    unrealizedPnl = unrealizedPnl,
                    candleTime = candle.openTime,
                ),
            )
            log.debug { "Created balance snapshot: ${state.accountBalance}" }
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
        ctx: SessionContext,
        error: Throwable,
    ) {
        val session = sessionRepository.findById(ctx.sessionId) ?: return
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
