package com.trading.coinflip.simulation

import com.trading.coinflip.config.BacktestProperties
import com.trading.coinflip.config.TradingConfig
import com.trading.coinflip.data.CandleRepository
import com.trading.coinflip.dto.CandleDto
import com.trading.coinflip.dto.OpenPositionDto
import com.trading.coinflip.dto.SimulationInitRequest
import com.trading.coinflip.dto.SimulationMetricsDto
import com.trading.coinflip.dto.SimulationStateDto
import com.trading.coinflip.dto.TradeDto
import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.PositionSide
import com.trading.coinflip.model.Timeframe
import com.trading.coinflip.trading.TradingProcessor
import com.trading.coinflip.trading.TradingState
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
class SimulationService(
    private val candleRepository: CandleRepository,
    private val properties: BacktestProperties,
    private val tradingProcessor: TradingProcessor,
) {
    private val log = KotlinLogging.logger {}

    private val lock = ReentrantReadWriteLock()

    // Global simulation state
    private var initialized = false
    private var symbol: String? = null
    private var timeframe: Timeframe? = null
    private var initialCapital: BigDecimal = BigDecimal.ZERO
    private var candles: List<Candle> = emptyList()
    private var allCandles: List<Candle> = emptyList() // All loaded candles before filtering
    private var currentCandleIndex: Int = -1

    // Trading state
    private var tradingState: TradingState? = null
    private var tradingConfig: TradingConfig? = null

    /**
     * Initialize simulation with symbol and timeframe
     */
    fun initialize(request: SimulationInitRequest): SimulationStateDto =
        lock.write {
            log.info { "Initializing simulation for ${request.symbol} ${request.timeframe}" }

            val tf =
                Timeframe.fromLabel(request.timeframe)
                    ?: throw IllegalArgumentException("Invalid timeframe: ${request.timeframe}")

            // Load candles from database
            val loadedCandles =
                candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(
                    request.symbol,
                    tf,
                )

            if (loadedCandles.isEmpty()) {
                throw IllegalStateException("No candles found for ${request.symbol} ${request.timeframe}")
            }

            // Filter candles by date range if specified
            val startDate = request.startDate?.let { Instant.parse(it) }
            val endDate = request.endDate?.let { Instant.parse(it) }

            val filteredCandles =
                loadedCandles.filter { candle ->
                    val afterStart = startDate?.let { candle.openTime >= it } ?: true
                    val beforeEnd = endDate?.let { candle.openTime <= it } ?: true
                    afterStart && beforeEnd
                }

            if (filteredCandles.isEmpty()) {
                throw IllegalStateException("No candles found in the specified date range")
            }

            // Use initial capital from config
            val configInitialCapital = properties.initialCapital

            // Reset all state
            symbol = request.symbol
            timeframe = tf
            initialCapital = configInitialCapital
            allCandles = loadedCandles
            candles = filteredCandles
            currentCandleIndex = -1 // Start before first candle

            // Create new state
            tradingState = TradingState.create(initialCapital)
            tradingConfig = properties.trading
            initialized = true

            log.info { "Simulation initialized with ${candles.size} candles (${loadedCandles.size} total available)" }

            getCurrentStateInternal()
        }

    /**
     * Advance to next candle
     */
    fun advanceCandle(): SimulationStateDto =
        lock.write {
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
    fun previousCandle(): SimulationStateDto =
        lock.write {
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
    fun reset(): SimulationStateDto =
        lock.write {
            checkInitialized()

            currentCandleIndex = -1
            tradingState!!.reset(initialCapital)

            log.info { "Simulation reset" }
            getCurrentStateInternal()
        }

    /**
     * Get current simulation state
     */
    fun getCurrentState(): SimulationStateDto =
        lock.read {
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
        tradingProcessor.processCandle(tradingState!!, candle, candles, currentCandleIndex, tradingConfig!!)
    }

    /**
     * Replay simulation from beginning to current index
     * Used for backward navigation
     */
    private fun replayToCurrentIndex() {
        // Reset state
        tradingState!!.reset(initialCapital)

        // Replay all candles up to current index
        for (i in 0..currentCandleIndex) {
            val savedIndex = currentCandleIndex
            currentCandleIndex = i
            processCurrentCandle()
            currentCandleIndex = savedIndex
        }
    }

    /**
     * Build current state DTO (internal, no locking)
     */
    private fun getCurrentStateInternal(): SimulationStateDto {
        val state = tradingState
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

        // Handle uninitialized state
        if (state == null) {
            return SimulationStateDto(
                initialized = false,
                symbol = null,
                timeframe = null,
                currentCandleIndex = -1,
                totalCandles = 0,
                currentCandle = null,
                previousCandle = null,
                metrics =
                    SimulationMetricsDto(
                        accountBalance = BigDecimal.ZERO,
                        peakBalance = BigDecimal.ZERO,
                        drawdown = BigDecimal.ZERO,
                        drawdownPercent = BigDecimal.ZERO,
                        totalTrades = 0,
                        winningTrades = 0,
                        losingTrades = 0,
                        winRate = BigDecimal.ZERO,
                        openPositions = 0,
                        allocatedCapital = BigDecimal.ZERO,
                        availableCapital = BigDecimal.ZERO,
                    ),
                openPositions = emptyList(),
                closedTrades = emptyList(),
            )
        }

        // Calculate allocated capital
        val allocatedCapital = state.openPositions.sumOf { it.allocatedCapital }
        val availableCapital = state.accountBalance - allocatedCapital

        // Calculate win rate using compareTo for safer BigDecimal comparison
        val winningTrades = state.closedTrades.count { it.profitLoss > BigDecimal.ZERO }
        val losingTrades = state.closedTrades.count { it.profitLoss < BigDecimal.ZERO }
        val winRate =
            if (state.closedTrades.isNotEmpty()) {
                // Use divide() with scale to prevent truncation: 1/2 = 0.5, not 0
                BigDecimal(winningTrades)
                    .divide(BigDecimal(state.closedTrades.size), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        // Calculate drawdown percent
        val drawdownPercent =
            if (state.peakBalance > BigDecimal.ZERO) {
                (state.maxDrawdown / state.peakBalance * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        return SimulationStateDto(
            initialized = initialized,
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
                    accountBalance = state.accountBalance.setScale(2, RoundingMode.HALF_UP),
                    peakBalance = state.peakBalance.setScale(2, RoundingMode.HALF_UP),
                    drawdown = state.maxDrawdown.setScale(2, RoundingMode.HALF_UP),
                    drawdownPercent = drawdownPercent,
                    totalTrades = state.closedTrades.size,
                    winningTrades = winningTrades,
                    losingTrades = losingTrades,
                    winRate = winRate,
                    openPositions = state.openPositions.size,
                    allocatedCapital = allocatedCapital.setScale(2, RoundingMode.HALF_UP),
                    availableCapital = availableCapital.setScale(2, RoundingMode.HALF_UP),
                ),
            openPositions =
                state.openPositions.map { position ->
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
                state.closedTrades.map { trade ->
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
