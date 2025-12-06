package com.trading.coinflip.simulation

import com.trading.coinflip.api.simulation.SimulationInitRequest
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.candle.CandleRepository
import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.dto.TradeDto
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.TradingProcessor
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.TradingState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class SimulationService(
    private val candleRepository: CandleRepository,
    private val properties: BacktestProperties,
    private val tradingProcessor: TradingProcessor,
) {
    private val log = KotlinLogging.logger {}

    private val mutex = Mutex()

    // Global simulation state
    private var initialized = false
    private var symbol: String? = null
    private var timeframe: Timeframe? = null
    private var initialCapital: BigDecimal = BigDecimal.ZERO
    private var candles: List<CandleEntity> = emptyList()
    private var allCandles: List<CandleEntity> = emptyList() // All loaded candles before filtering
    private var currentCandleIndex: Int = -1

    // Trading state
    private lateinit var tradingState: TradingState

    /**
     * Initialize simulation with symbol and timeframe
     */
    suspend fun initialize(request: SimulationInitRequest): SimulationStateDto =
        mutex.withLock {
            log.info { "Initializing simulation for ${request.symbol} ${request.timeframe.label}" }

            // Load candles from database
            // todo fix it
            val loadedCandles =
                candleRepository
                    .findBySymbolAndTimeframeOrderByOpenTimeAsc(
                        request.symbol,
                        request.timeframe,
                    ).toList()

            if (loadedCandles.isEmpty()) {
                throw IllegalStateException("No candles found for ${request.symbol} ${request.timeframe}")
            }

            // Filter candles by date range if specified
            val filteredCandles =
                loadedCandles.filter { candle ->
                    val afterStart = request.startDate?.let { candle.openTime >= it } ?: true
                    val beforeEnd = request.endDate?.let { candle.openTime <= it } ?: true
                    afterStart && beforeEnd
                }

            if (filteredCandles.isEmpty()) {
                throw IllegalStateException("No candles found in the specified date range")
            }

            // Use initial capital from config
            val configInitialCapital = properties.initialCapital

            // Reset all state
            symbol = request.symbol
            timeframe = request.timeframe
            initialCapital = configInitialCapital
            allCandles = loadedCandles
            candles = filteredCandles
            currentCandleIndex = -1 // Start before first candle

            // Create new state
            tradingState = TradingState.create(initialCapital)
            initialized = true

            log.info { "Simulation initialized with ${candles.size} candles (${loadedCandles.size} total available)" }

            getCurrentStateInternal()
        }

    /**
     * Advance to next candle
     */
    suspend fun advanceCandle(): SimulationStateDto =
        mutex.withLock {
            checkInitialized()

            if (currentCandleIndex >= candles.size - 1) {
                throw IllegalStateException("Already at last candle")
            }

            currentCandleIndex++
            processCurrentCandle()

            log.debug { "Advanced to candle $currentCandleIndex" }
            getCurrentStateInternal()
        }

    /**
     * Go back to previous candle
     */
    suspend fun previousCandle(): SimulationStateDto =
        mutex.withLock {
            checkInitialized()

            if (currentCandleIndex <= 0) {
                throw IllegalStateException("Already at first candle")
            }

            currentCandleIndex--
            replayToCurrentIndex()

            log.debug { "Moved back to candle $currentCandleIndex" }
            getCurrentStateInternal()
        }

    /**
     * Reset simulation to beginning
     */
    suspend fun reset(): SimulationStateDto =
        mutex.withLock {
            checkInitialized()

            currentCandleIndex = -1
            tradingState = TradingState.create(initialCapital)

            log.info { "Simulation reset" }
            getCurrentStateInternal()
        }

    /**
     * Get current simulation state
     */
    suspend fun getCurrentState(): SimulationStateDto =
        mutex.withLock {
            getCurrentStateInternal()
        }

    /**
     * Process current candle (update positions, close/open trades)
     */
    private fun processCurrentCandle() {
        if (currentCandleIndex < 0 || currentCandleIndex >= candles.size) {
            return
        }

        val candle = candles[currentCandleIndex]
        val events = tradingProcessor.processCandle(tradingState, candle)
        tradingState = tradingState.applyEvents(events)
    }

    /**
     * Replay simulation from beginning to current index
     * Used for backward navigation
     */
    private fun replayToCurrentIndex() {
        // Reset state
        tradingState = TradingState.create(initialCapital)

        // Replay all candles up to current index
        val targetIndex = currentCandleIndex
        for (i in 0..targetIndex) {
            val candle = candles[i]
            val events = tradingProcessor.processCandle(tradingState, candle)
            tradingState = tradingState.applyEvents(events)
        }
    }

    /**
     * Build current state DTO (internal, no locking)
     */
    private fun getCurrentStateInternal(): SimulationStateDto {
        // Handle uninitialized state
        if (!initialized) {
            return SimulationStateDto()
        }

        val currentCandle =
            if (currentCandleIndex >= 0 && currentCandleIndex < candles.size) {
                candles[currentCandleIndex]
            } else {
                null
            }

        val previousCandle =
            if (currentCandleIndex > 0 && currentCandleIndex <= candles.size) {
                candles[currentCandleIndex - 1]
            } else {
                null
            }

        // Calculate current price for unrealized P/L
        val currentPrice = currentCandle?.close ?: BigDecimal.ZERO

        // Calculate allocated capital
        val allocatedCapital = tradingState.openPositions.sumOf { it.allocatedCapital }
        val availableCapital = tradingState.accountBalance - allocatedCapital

        // Calculate win rate using compareTo for safer BigDecimal comparison
        val winningTrades = tradingState.closedTrades.count { it.profitLoss > BigDecimal.ZERO }
        val losingTrades = tradingState.closedTrades.count { it.profitLoss < BigDecimal.ZERO }
        val winRate =
            if (tradingState.closedTrades.isNotEmpty()) {
                // Use divide() with scale to prevent truncation: 1/2 = 0.5, not 0
                BigDecimal(winningTrades)
                    .divide(BigDecimal(tradingState.closedTrades.size), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        // Calculate drawdown percent
        val drawdownPercent =
            if (tradingState.peakBalance > BigDecimal.ZERO) {
                (tradingState.maxDrawdown / tradingState.peakBalance * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        return SimulationStateDto(
            initialized = true,
            symbol = symbol,
            timeframe = timeframe?.label,
            currentCandleIndex = currentCandleIndex,
            totalCandles = candles.size,
            currentCandle =
                currentCandle?.let {
                    CandleDto(
                        openTime = it.openTime,
                        open = it.open,
                        high = it.high,
                        low = it.low,
                        close = it.close,
                        volume = it.volume,
                        atr = it.atr,
                    )
                },
            previousCandle =
                previousCandle?.let {
                    CandleDto(
                        openTime = it.openTime,
                        open = it.open,
                        high = it.high,
                        low = it.low,
                        close = it.close,
                        volume = it.volume,
                        atr = it.atr,
                    )
                },
            metrics =
                SimulationMetricsDto(
                    accountBalance = tradingState.accountBalance.setScale(2, RoundingMode.HALF_UP),
                    peakBalance = tradingState.peakBalance.setScale(2, RoundingMode.HALF_UP),
                    drawdown = tradingState.maxDrawdown.setScale(2, RoundingMode.HALF_UP),
                    drawdownPercent = drawdownPercent,
                    totalTrades = tradingState.closedTrades.size,
                    winningTrades = winningTrades,
                    losingTrades = losingTrades,
                    winRate = winRate,
                    openPositions = tradingState.openPositions.size,
                    allocatedCapital = allocatedCapital.setScale(2, RoundingMode.HALF_UP),
                    availableCapital = availableCapital.setScale(2, RoundingMode.HALF_UP),
                ),
            openPositions =
                tradingState.openPositions.map { position ->
                    val unrealizedPnL =
                        when (position.side) {
                            PositionSide.LONG -> (currentPrice - position.entryPrice) * position.positionSize
                            PositionSide.SHORT -> (position.entryPrice - currentPrice) * position.positionSize
                        }
                    val positionValue = position.entryPrice * position.positionSize
                    val unrealizedPnLPercent =
                        if (positionValue > BigDecimal.ZERO) {
                            (unrealizedPnL / positionValue * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                        } else {
                            BigDecimal.ZERO
                        }

                    OpenPositionDto(
                        id = position.id,
                        symbol = position.symbol,
                        side = position.side,
                        entryTime = position.entryTime,
                        entryPrice = position.entryPrice,
                        currentPrice = currentPrice,
                        positionSize = position.positionSize,
                        initialStopLoss = position.initialStopLoss,
                        trailingStop = position.trailingStop,
                        unrealizedPnL = unrealizedPnL.setScale(2, RoundingMode.HALF_UP),
                        unrealizedPnLPercent = unrealizedPnLPercent,
                        allocatedCapital = position.allocatedCapital.setScale(2, RoundingMode.HALF_UP),
                    )
                },
            closedTrades =
                tradingState.closedTrades.map { trade ->
                    TradeDto(
                        id = trade.id,
                        symbol = trade.symbol,
                        side = trade.side,
                        entryTime = trade.entryTime,
                        entryPrice = trade.entryPrice,
                        exitTime = trade.exitTime,
                        exitPrice = trade.exitPrice,
                        positionSize = trade.positionSize,
                        profitLoss = trade.profitLoss,
                        profitLossPercent = trade.profitLossPercent,
                        exitReason = trade.exitReason,
                        balanceBeforeOpen = trade.balanceBeforeOpen,
                        balanceAfterOpen = trade.balanceAfterOpen,
                        balanceBeforeClose = trade.balanceBeforeClose,
                        balanceAfterClose = trade.balanceAfterClose,
                    )
                },
        )
    }

    private fun checkInitialized() {
        if (!initialized) {
            throw IllegalStateException("Simulation not initialized. Call initialize() first.")
        }
    }
}
