package com.trading.coinflip.simulation

import com.trading.coinflip.config.BacktestProperties
import com.trading.coinflip.data.CandleRepository
import com.trading.coinflip.dto.CandleDto
import com.trading.coinflip.dto.OpenPositionDto
import com.trading.coinflip.dto.SimulationInitRequest
import com.trading.coinflip.dto.SimulationMetricsDto
import com.trading.coinflip.dto.SimulationStateDto
import com.trading.coinflip.dto.TradeDto
import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.Position
import com.trading.coinflip.model.PositionSide
import com.trading.coinflip.model.PositionStatus
import com.trading.coinflip.model.Timeframe
import com.trading.coinflip.model.Trade
import com.trading.coinflip.strategy.CoinFlipStrategy
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
    private val strategy: CoinFlipStrategy,
    private val properties: BacktestProperties,
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
    private var accountBalance: BigDecimal = BigDecimal.ZERO
    private var peakBalance: BigDecimal = BigDecimal.ZERO
    private var maxDrawdown: BigDecimal = BigDecimal.ZERO
    private val openPositions = mutableListOf<Position>()
    private val closedTrades = mutableListOf<Trade>()
    private var tradeIdCounter = 0L

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
            accountBalance = initialCapital
            peakBalance = initialCapital
            maxDrawdown = BigDecimal.ZERO
            openPositions.clear()
            closedTrades.clear()
            tradeIdCounter = 0L
            strategy.reset()
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
            accountBalance = initialCapital
            peakBalance = initialCapital
            maxDrawdown = BigDecimal.ZERO
            openPositions.clear()
            closedTrades.clear()
            tradeIdCounter = 0L
            strategy.reset()

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

        // Update existing positions and check for stops
        val positionsToClose = mutableListOf<Position>()
        for (position in openPositions) {
            val shouldClose =
                strategy.updatePosition(
                    position = position,
                    candle = candle,
                    candles = candles,
                    candleIndex = currentCandleIndex,
                    atrMultiplier = properties.atrMultiplier,
                )

            if (shouldClose) {
                positionsToClose.add(position)
            }
        }

        // Close positions and update balance
        for (position in positionsToClose) {
            closePosition(position)
        }

        // Consider opening new position if we have capacity
        if (openPositions.size < properties.maxConcurrentPositions &&
            candle.atr != null &&
            accountBalance > BigDecimal.ZERO
        ) {
            // Calculate capital already allocated to open positions
            val allocatedCapital = openPositions.sumOf { it.allocatedCapital }
            val availableBalance = accountBalance - allocatedCapital

            // Only try to open if we have available capital
            if (availableBalance > BigDecimal.ZERO) {
                // Random entry frequency: try to enter on ~10% of candles
                if (kotlin.random.Random.nextDouble() < 0.1) {
                    val newPosition =
                        strategy.createPosition(
                            candle = candle,
                            candles = candles,
                            candleIndex = currentCandleIndex,
                            accountBalance = availableBalance,
                            riskPercent = properties.riskPerTrade,
                            atrMultiplier = properties.atrMultiplier,
                            balanceBeforeOpen = availableBalance,
                        )

                    if (newPosition != null) {
                        openPositions.add(newPosition)
                        log.debug { "Opened new ${newPosition.side} position at ${newPosition.entryPrice}" }
                    }
                }
            }
        }
    }

    /**
     * Close a position and update balances
     */
    private fun closePosition(position: Position) {
        openPositions.remove(position)
        position.status = PositionStatus.CLOSED

        val balanceBeforeClose = accountBalance

        // Calculate P&L and transaction costs
        val pnl =
            when (position.side) {
                PositionSide.LONG -> (position.exitPrice!! - position.entryPrice) * position.positionSize
                PositionSide.SHORT -> (position.entryPrice - position.exitPrice!!) * position.positionSize
            }
        val transactionCost =
            position.entryPrice * position.positionSize *
                (properties.transactionCostPercent / BigDecimal(100)) * BigDecimal(2) // Entry + Exit

        accountBalance += pnl - transactionCost
        val balanceAfterClose = accountBalance

        val trade = position.toTrade(++tradeIdCounter, balanceBeforeClose, balanceAfterClose)
        closedTrades.add(trade)

        log.info {
            "✓ Trade #${trade.id} CLOSED: ${trade.side}, " +
                "P&L=${trade.profitLoss}, " +
                "scale=${trade.profitLoss.scale()}, " +
                "compareTo(ZERO)=${trade.profitLoss.compareTo(BigDecimal.ZERO)}, " +
                "Total closed trades: ${closedTrades.size}"
        }

        // Track drawdown
        if (accountBalance > peakBalance) {
            peakBalance = accountBalance
        }
        val currentDrawdown = peakBalance - accountBalance
        if (currentDrawdown > maxDrawdown) {
            maxDrawdown = currentDrawdown
        }
    }

    /**
     * Replay simulation from beginning to current index
     * Used for backward navigation
     */
    private fun replayToCurrentIndex() {
        // Reset state
        accountBalance = initialCapital
        peakBalance = initialCapital
        maxDrawdown = BigDecimal.ZERO
        openPositions.clear()
        closedTrades.clear()
        tradeIdCounter = 0L
        strategy.reset()

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
        val allocatedCapital = openPositions.sumOf { it.allocatedCapital }
        val availableCapital = accountBalance - allocatedCapital

        // Calculate win rate using compareTo for safer BigDecimal comparison
        val winningTrades = closedTrades.count { it.profitLoss > BigDecimal.ZERO }
        val losingTrades = closedTrades.count { it.profitLoss < BigDecimal.ZERO }
        val winRate =
            if (closedTrades.isNotEmpty()) {
                // Use divide() with scale to prevent truncation: 1/2 = 0.5, not 0
                BigDecimal(winningTrades)
                    .divide(BigDecimal(closedTrades.size), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        // Calculate drawdown percent
        val drawdownPercent =
            if (peakBalance > BigDecimal.ZERO) {
                (maxDrawdown / peakBalance * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
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
                    accountBalance = accountBalance.setScale(2, RoundingMode.HALF_UP),
                    peakBalance = peakBalance.setScale(2, RoundingMode.HALF_UP),
                    drawdown = maxDrawdown.setScale(2, RoundingMode.HALF_UP),
                    drawdownPercent = drawdownPercent,
                    totalTrades = closedTrades.size,
                    winningTrades = winningTrades,
                    losingTrades = losingTrades,
                    winRate = winRate,
                    openPositions = openPositions.size,
                    allocatedCapital = allocatedCapital.setScale(2, RoundingMode.HALF_UP),
                    availableCapital = availableCapital.setScale(2, RoundingMode.HALF_UP),
                ),
            openPositions =
                openPositions.map { position ->
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
                closedTrades.map { trade ->
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
