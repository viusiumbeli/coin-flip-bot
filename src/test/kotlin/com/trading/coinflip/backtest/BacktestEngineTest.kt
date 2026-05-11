package com.trading.coinflip.backtest

import com.trading.coinflip.analytics.PerformanceAnalytics
import com.trading.coinflip.backtest.model.BacktestConfig
import com.trading.coinflip.backtest.model.BacktestResult
import com.trading.coinflip.common.config.TradingConfig
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.TradingProcessor
import com.trading.coinflip.engine.model.Position
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.Trade
import com.trading.coinflip.engine.model.TradingEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class BacktestEngineTest {
    private lateinit var processor: TradingProcessor
    private lateinit var analytics: PerformanceAnalytics
    private lateinit var engine: BacktestEngine

    companion object {
        private const val TEST_SYMBOL = "BTCUSDT"
        private val TEST_TIMEFRAME = Timeframe.ONE_HOUR
        private val BASE_TIME = Instant.parse("2024-01-01T00:00:00Z")
        private val INITIAL_CAPITAL = BigDecimal("10000")
    }

    @BeforeEach
    fun setUp() {
        processor = mockk()
        analytics = mockk()
        engine = BacktestEngine(processor, analytics)
    }

    // --- Helper Methods ---

    private fun createTestCandle(
        index: Int,
        price: BigDecimal = BigDecimal("100"),
        atr: BigDecimal? = BigDecimal("10"),
    ): CandleEntity =
        CandleEntity(
            id = index.toLong(),
            symbol = TEST_SYMBOL,
            timeframe = TEST_TIMEFRAME,
            openTime = BASE_TIME.plus(index.toLong(), ChronoUnit.HOURS),
            open = price,
            high = price + BigDecimal("5"),
            low = price - BigDecimal("5"),
            close = price,
            volume = BigDecimal("1000"),
            atr = atr,
        )

    private fun createTestConfig(collectTrades: Boolean = false): BacktestConfig =
        BacktestConfig(
            symbol = TEST_SYMBOL,
            timeframe = TEST_TIMEFRAME,
            initialCapital = INITIAL_CAPITAL,
            trading = TradingConfig(),
            collectTrades = collectTrades,
        )

    private fun createTestPosition(
        id: Long = 1L,
        side: PositionSide = PositionSide.LONG,
        entryPrice: BigDecimal = BigDecimal("100"),
        positionSize: BigDecimal = BigDecimal("1"),
    ): Position =
        Position(
            id = id,
            symbol = TEST_SYMBOL,
            timeframe = TEST_TIMEFRAME,
            side = side,
            entryTime = BASE_TIME,
            entryPrice = entryPrice,
            positionSize = positionSize,
            initialStopLoss = entryPrice - BigDecimal("30"),
            trailingStop = entryPrice - BigDecimal("30"),
            highestFavorablePrice = entryPrice,
            status = PositionStatus.OPEN,
            balanceBeforeOpen = INITIAL_CAPITAL,
            balanceAfterOpen = INITIAL_CAPITAL,
            allocatedCapital = entryPrice * positionSize,
        )

    private fun createTestTrade(
        id: Long = 1L,
        pnl: BigDecimal = BigDecimal("100"),
    ): Trade =
        Trade(
            id = id,
            symbol = TEST_SYMBOL,
            timeframe = TEST_TIMEFRAME,
            side = PositionSide.LONG,
            entryTime = BASE_TIME,
            entryPrice = BigDecimal("100"),
            exitTime = BASE_TIME.plus(1, ChronoUnit.HOURS),
            exitPrice = BigDecimal("110"),
            positionSize = BigDecimal("1"),
            initialStopLoss = BigDecimal("70"),
            trailingStop = BigDecimal("80"),
            profitLoss = pnl,
            profitLossPercent = BigDecimal("10"),
            exitReason = "Stop hit",
            balanceBeforeOpen = INITIAL_CAPITAL,
            balanceAfterOpen = INITIAL_CAPITAL,
            balanceBeforeClose = INITIAL_CAPITAL,
            balanceAfterClose = INITIAL_CAPITAL + pnl,
        )

    private fun createEmptyBacktestResult(config: BacktestConfig): BacktestResult =
        BacktestResult(
            config = config,
            initialCapital = config.initialCapital,
            finalCapital = config.initialCapital,
            totalReturn = BigDecimal.ZERO,
            totalReturnPercent = BigDecimal.ZERO,
            maxDrawdown = BigDecimal.ZERO,
            maxDrawdownPercent = BigDecimal.ZERO,
            winRate = BigDecimal.ZERO,
            profitFactor = BigDecimal.ZERO,
            sharpeRatio = BigDecimal.ZERO,
            totalTrades = 0,
            winningTrades = 0,
            losingTrades = 0,
            averageWin = BigDecimal.ZERO,
            averageLoss = BigDecimal.ZERO,
            largestWin = BigDecimal.ZERO,
            largestLoss = BigDecimal.ZERO,
            averageTradeDuration = 0,
            startDate = BASE_TIME,
            endDate = BASE_TIME,
            buyAndHoldReturn = BigDecimal.ZERO,
            buyAndHoldReturnPercent = BigDecimal.ZERO,
        )

    @Nested
    @DisplayName("Empty Candles")
    inner class EmptyCandlesTests {
        @Test
        @DisplayName("returns empty result when candles list is empty")
        fun emptyCandles_returnsEmptyResult() {
            val config = createTestConfig()

            val result = engine.runBacktest(config, emptyList())

            assertThat(result.trades).isEmpty()
            assertThat(result.totalTrades).isEqualTo(0)
            assertThat(result.initialCapital).isEqualTo(INITIAL_CAPITAL)
            assertThat(result.finalCapital).isEqualTo(INITIAL_CAPITAL)
            assertThat(result.totalReturn).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.buyAndHoldReturn).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        @DisplayName("does not call processor or analytics when candles empty")
        fun emptyCandles_noProcessorCalls() {
            val config = createTestConfig()

            engine.runBacktest(config, emptyList())

            verify(exactly = 0) { processor.processCandle(any(), any()) }
            verify(exactly = 0) { analytics.calculatePerformance(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("Single Candle")
    inner class SingleCandleTests {
        @Test
        @DisplayName("processes single candle through processor")
        fun singleCandle_processesCandle() {
            val config = createTestConfig()
            val candle = createTestCandle(0)
            val expectedResult = createEmptyBacktestResult(config)

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                    trades = any(),
                )
            } returns expectedResult

            engine.runBacktest(config, listOf(candle))

            verify(exactly = 1) { processor.processCandle(any(), candle) }
        }

        @Test
        @DisplayName("buy and hold is zero when single candle")
        fun singleCandle_buyAndHoldZero() {
            val config = createTestConfig()
            val candle = createTestCandle(0, price = BigDecimal("100"))

            val buyAndHoldSlot = slot<BigDecimal>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = capture(buyAndHoldSlot),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, listOf(candle))

            assertThat(buyAndHoldSlot.captured).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @Nested
    @DisplayName("Multiple Candles Without Trades")
    inner class MultipleCandlesNoTradesTests {
        @Test
        @DisplayName("processes all candles through processor")
        fun multipleCandlesNoTrades_processesAllCandles() {
            val config = createTestConfig()
            val candles = (0 until 5).map { createTestCandle(it) }
            val expectedResult = createEmptyBacktestResult(config)

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                    trades = any(),
                )
            } returns expectedResult

            engine.runBacktest(config, candles)

            verify(exactly = 5) { processor.processCandle(any(), any()) }
        }

        @Test
        @DisplayName("calculates buy and hold correctly for price increase")
        fun priceIncrease_buyAndHoldPositive() {
            val config = createTestConfig()
            val candles =
                listOf(
                    createTestCandle(0, price = BigDecimal("100")),
                    createTestCandle(1, price = BigDecimal("150")),
                )

            val buyAndHoldSlot = slot<BigDecimal>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = capture(buyAndHoldSlot),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            // Buy and hold: (150 - 100) / 100 * 10000 = 5000
            assertThat(buyAndHoldSlot.captured).isEqualByComparingTo(BigDecimal("5000"))
        }

        @Test
        @DisplayName("calculates buy and hold correctly for price decrease")
        fun priceDecrease_buyAndHoldNegative() {
            val config = createTestConfig()
            val candles =
                listOf(
                    createTestCandle(0, price = BigDecimal("100")),
                    createTestCandle(1, price = BigDecimal("80")),
                )

            val buyAndHoldSlot = slot<BigDecimal>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = capture(buyAndHoldSlot),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            // Buy and hold: (80 - 100) / 100 * 10000 = -2000
            assertThat(buyAndHoldSlot.captured).isEqualByComparingTo(BigDecimal("-2000"))
        }

        @Test
        @DisplayName("passes correct dates to analytics")
        fun passesDatesToAnalytics() {
            val config = createTestConfig()
            val candles = (0 until 3).map { createTestCandle(it) }

            val startDateSlot = slot<Instant>()
            val endDateSlot = slot<Instant>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = capture(startDateSlot),
                    endDate = capture(endDateSlot),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            assertThat(startDateSlot.captured).isEqualTo(candles.first().openTime)
            assertThat(endDateSlot.captured).isEqualTo(candles.last().openTime)
        }
    }

    @Nested
    @DisplayName("Multiple Candles With Trades")
    inner class MultipleCandlesWithTradesTests {
        @Test
        @DisplayName("applies position opened events to state")
        fun positionOpened_appliedToState() {
            val config = createTestConfig()
            val candles = (0 until 3).map { createTestCandle(it) }
            val position = createTestPosition()
            val positionOpenedEvent =
                TradingEvent.PositionOpened(
                    position = position,
                    newPositionIdCounter = 1L,
                )

            every { processor.processCandle(any(), any()) } returnsMany
                listOf(
                    listOf(positionOpenedEvent), // First candle opens position
                    emptyList(), // Second candle no event
                    emptyList(), // Third candle no event
                )

            // Position should be force closed at end
            val trade = createTestTrade()
            val closeEvent =
                TradingEvent.PositionClosed(
                    positionId = position.id,
                    exitPrice = candles.last().close,
                    exitTime = candles.last().openTime,
                    exitReason = "End of backtest period",
                    pnl = BigDecimal.ZERO,
                    transactionCost = BigDecimal.ZERO,
                    trade = trade,
                    newBalance = INITIAL_CAPITAL,
                    newPeakBalance = INITIAL_CAPITAL,
                    newMaxDrawdown = BigDecimal.ZERO,
                    newTradeIdCounter = 1L,
                )

            every { processor.forceClosePosition(any(), any(), any(), any(), any()) } returns closeEvent

            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                    trades = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            verify(exactly = 1) {
                processor.forceClosePosition(
                    state = any(),
                    position = any(),
                    exitPrice = candles.last().close,
                    exitTime = candles.last().openTime,
                    exitReason = "End of backtest period",
                )
            }
        }

        @Test
        @DisplayName("returns closed trades when collectTrades is true")
        fun closedTrades_returnedInResult() {
            val config = createTestConfig(collectTrades = true)
            val candles = (0 until 2).map { createTestCandle(it) }
            val position = createTestPosition()
            val trade = createTestTrade()

            // First candle opens position, second closes it
            val positionOpenedEvent =
                TradingEvent.PositionOpened(
                    position = position,
                    newPositionIdCounter = 1L,
                )
            val positionClosedEvent =
                TradingEvent.PositionClosed(
                    positionId = position.id,
                    exitPrice = BigDecimal("110"),
                    exitTime = candles[1].openTime,
                    exitReason = "Stop hit",
                    pnl = BigDecimal("10"),
                    transactionCost = BigDecimal("0.2"),
                    trade = trade,
                    newBalance = INITIAL_CAPITAL + BigDecimal("10"),
                    newPeakBalance = INITIAL_CAPITAL + BigDecimal("10"),
                    newMaxDrawdown = BigDecimal.ZERO,
                    newTradeIdCounter = 1L,
                )

            every { processor.processCandle(any(), any()) } returnsMany
                listOf(
                    listOf(positionOpenedEvent),
                    listOf(positionClosedEvent),
                )

            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                    trades = any(),
                )
            } answers {
                // Return the captured trades in the result
                val capturedTrades = arg<List<Trade>>(8)
                createEmptyBacktestResult(config).copy(trades = capturedTrades)
            }

            val result = engine.runBacktest(config, candles)

            assertThat(result.trades).hasSize(1)
            assertThat(result.trades[0]).isEqualTo(trade)
        }
    }

    @Nested
    @DisplayName("Force Close Behavior")
    inner class ForceCloseTests {
        @Test
        @DisplayName("force closes all open positions at end of backtest")
        fun forceClosesAllOpenPositions() {
            val config = createTestConfig()
            val candles = (0 until 2).map { createTestCandle(it) }
            val position1 = createTestPosition(id = 1)
            val position2 = createTestPosition(id = 2, side = PositionSide.SHORT)

            // Open two positions
            every { processor.processCandle(any(), any()) } returnsMany
                listOf(
                    listOf(
                        TradingEvent.PositionOpened(position = position1, newPositionIdCounter = 1L),
                        TradingEvent.PositionOpened(position = position2, newPositionIdCounter = 2L),
                    ),
                    emptyList(),
                )

            // Force close both
            val trade1 = createTestTrade(id = 1)
            val trade2 = createTestTrade(id = 2)
            every {
                processor.forceClosePosition(any(), match { it.id == 1L }, any(), any(), any())
            } returns
                TradingEvent.PositionClosed(
                    positionId = 1L,
                    exitPrice = candles.last().close,
                    exitTime = candles.last().openTime,
                    exitReason = "End of backtest period",
                    pnl = BigDecimal.ZERO,
                    transactionCost = BigDecimal.ZERO,
                    trade = trade1,
                    newBalance = INITIAL_CAPITAL,
                    newPeakBalance = INITIAL_CAPITAL,
                    newMaxDrawdown = BigDecimal.ZERO,
                    newTradeIdCounter = 1L,
                )
            every {
                processor.forceClosePosition(any(), match { it.id == 2L }, any(), any(), any())
            } returns
                TradingEvent.PositionClosed(
                    positionId = 2L,
                    exitPrice = candles.last().close,
                    exitTime = candles.last().openTime,
                    exitReason = "End of backtest period",
                    pnl = BigDecimal.ZERO,
                    transactionCost = BigDecimal.ZERO,
                    trade = trade2,
                    newBalance = INITIAL_CAPITAL,
                    newPeakBalance = INITIAL_CAPITAL,
                    newMaxDrawdown = BigDecimal.ZERO,
                    newTradeIdCounter = 2L,
                )

            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                    trades = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            verify(exactly = 2) { processor.forceClosePosition(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("uses last candle close as exit price")
        fun usesLastCandleCloseAsExitPrice() {
            val config = createTestConfig()
            val lastPrice = BigDecimal("150")
            val candles =
                listOf(
                    createTestCandle(0, price = BigDecimal("100")),
                    createTestCandle(1, price = lastPrice),
                )
            val position = createTestPosition()

            every { processor.processCandle(any(), any()) } returnsMany
                listOf(
                    listOf(TradingEvent.PositionOpened(position = position, newPositionIdCounter = 1L)),
                    emptyList(),
                )

            val exitPriceSlot = slot<BigDecimal>()
            val trade = createTestTrade()
            every {
                processor.forceClosePosition(any(), any(), capture(exitPriceSlot), any(), any())
            } returns
                TradingEvent.PositionClosed(
                    positionId = 1L,
                    exitPrice = lastPrice,
                    exitTime = candles.last().openTime,
                    exitReason = "End of backtest period",
                    pnl = BigDecimal.ZERO,
                    transactionCost = BigDecimal.ZERO,
                    trade = trade,
                    newBalance = INITIAL_CAPITAL,
                    newPeakBalance = INITIAL_CAPITAL,
                    newMaxDrawdown = BigDecimal.ZERO,
                    newTradeIdCounter = 1L,
                )

            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                    trades = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            assertThat(exitPriceSlot.captured).isEqualByComparingTo(lastPrice)
        }

        @Test
        @DisplayName("uses 'End of backtest period' as exit reason")
        fun usesCorrectExitReason() {
            val config = createTestConfig()
            val candles = (0 until 2).map { createTestCandle(it) }
            val position = createTestPosition()

            every { processor.processCandle(any(), any()) } returnsMany
                listOf(
                    listOf(TradingEvent.PositionOpened(position = position, newPositionIdCounter = 1L)),
                    emptyList(),
                )

            val exitReasonSlot = slot<String>()
            val trade = createTestTrade()
            every {
                processor.forceClosePosition(any(), any(), any(), any(), capture(exitReasonSlot))
            } returns
                TradingEvent.PositionClosed(
                    positionId = 1L,
                    exitPrice = candles.last().close,
                    exitTime = candles.last().openTime,
                    exitReason = "End of backtest period",
                    pnl = BigDecimal.ZERO,
                    transactionCost = BigDecimal.ZERO,
                    trade = trade,
                    newBalance = INITIAL_CAPITAL,
                    newPeakBalance = INITIAL_CAPITAL,
                    newMaxDrawdown = BigDecimal.ZERO,
                    newTradeIdCounter = 1L,
                )

            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                    trades = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            assertThat(exitReasonSlot.captured).isEqualTo("End of backtest period")
        }
    }

    @Nested
    @DisplayName("State Tracking")
    inner class StateTrackingTests {
        @Test
        @DisplayName("passes final balance to analytics")
        fun passesFinalBalanceToAnalytics() {
            val config = createTestConfig()
            val candles = (0 until 2).map { createTestCandle(it) }
            val position = createTestPosition()
            val pnl = BigDecimal("500")
            val finalBalance = INITIAL_CAPITAL + pnl

            val positionOpenedEvent = TradingEvent.PositionOpened(position = position, newPositionIdCounter = 1L)
            val trade = createTestTrade(pnl = pnl)
            val positionClosedEvent =
                TradingEvent.PositionClosed(
                    positionId = position.id,
                    exitPrice = BigDecimal("110"),
                    exitTime = candles[1].openTime,
                    exitReason = "Stop hit",
                    pnl = pnl,
                    transactionCost = BigDecimal.ZERO,
                    trade = trade,
                    newBalance = finalBalance,
                    newPeakBalance = finalBalance,
                    newMaxDrawdown = BigDecimal.ZERO,
                    newTradeIdCounter = 1L,
                )

            every { processor.processCandle(any(), any()) } returnsMany
                listOf(
                    listOf(positionOpenedEvent),
                    listOf(positionClosedEvent),
                )

            val finalCapitalSlot = slot<BigDecimal>()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = capture(finalCapitalSlot),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            assertThat(finalCapitalSlot.captured).isEqualByComparingTo(finalBalance)
        }

        @Test
        @DisplayName("passes max drawdown to analytics")
        fun passesMaxDrawdownToAnalytics() {
            val config = createTestConfig()
            val candles = (0 until 2).map { createTestCandle(it) }
            val position = createTestPosition()
            val maxDrawdown = BigDecimal("200")

            val positionOpenedEvent = TradingEvent.PositionOpened(position = position, newPositionIdCounter = 1L)
            val trade = createTestTrade(pnl = BigDecimal("-200"))
            val positionClosedEvent =
                TradingEvent.PositionClosed(
                    positionId = position.id,
                    exitPrice = BigDecimal("90"),
                    exitTime = candles[1].openTime,
                    exitReason = "Stop hit",
                    pnl = BigDecimal("-200"),
                    transactionCost = BigDecimal.ZERO,
                    trade = trade,
                    newBalance = INITIAL_CAPITAL - BigDecimal("200"),
                    newPeakBalance = INITIAL_CAPITAL,
                    newMaxDrawdown = maxDrawdown,
                    newTradeIdCounter = 1L,
                )

            every { processor.processCandle(any(), any()) } returnsMany
                listOf(
                    listOf(positionOpenedEvent),
                    listOf(positionClosedEvent),
                )

            val maxDrawdownSlot = slot<BigDecimal>()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = capture(maxDrawdownSlot),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            assertThat(maxDrawdownSlot.captured).isEqualByComparingTo(maxDrawdown)
        }
    }

    @Nested
    @DisplayName("Buy and Hold Edge Cases")
    inner class BuyAndHoldEdgeCasesTests {
        @Test
        @DisplayName("buy and hold is zero when price unchanged")
        fun priceUnchanged_buyAndHoldZero() {
            val config = createTestConfig()
            val price = BigDecimal("100")
            val candles =
                listOf(
                    createTestCandle(0, price = price),
                    createTestCandle(1, price = price),
                    createTestCandle(2, price = price),
                )

            val buyAndHoldSlot = slot<BigDecimal>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = capture(buyAndHoldSlot),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            assertThat(buyAndHoldSlot.captured).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        @DisplayName("buy and hold calculated from first and last candle only")
        fun buyAndHold_ignoresMiddleCandles() {
            val config = createTestConfig()
            val candles =
                listOf(
                    createTestCandle(0, price = BigDecimal("100")),
                    createTestCandle(1, price = BigDecimal("500")), // This high shouldn't affect result
                    createTestCandle(2, price = BigDecimal("50")), // This low shouldn't affect result
                    createTestCandle(3, price = BigDecimal("200")),
                )

            val buyAndHoldSlot = slot<BigDecimal>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = capture(buyAndHoldSlot),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            // Buy and hold: (200 - 100) / 100 * 10000 = 10000
            assertThat(buyAndHoldSlot.captured).isEqualByComparingTo(BigDecimal("10000"))
        }

        @Test
        @DisplayName("buy and hold uses close prices not high/low")
        fun buyAndHold_usesClosePrices() {
            val config = createTestConfig()
            // First candle: close=100, high=105, low=95
            // Last candle: close=120, high=125, low=115
            val candles =
                listOf(
                    createTestCandle(0, price = BigDecimal("100")),
                    createTestCandle(1, price = BigDecimal("120")),
                )

            val buyAndHoldSlot = slot<BigDecimal>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = capture(buyAndHoldSlot),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            // Buy and hold: (120 - 100) / 100 * 10000 = 2000
            assertThat(buyAndHoldSlot.captured).isEqualByComparingTo(BigDecimal("2000"))
        }
    }

    @Nested
    @DisplayName("Config Passthrough")
    inner class ConfigPassthroughTests {
        @Test
        @DisplayName("passes config unchanged to analytics")
        fun passesConfigToAnalytics() {
            val config = createTestConfig()
            val candles = listOf(createTestCandle(0))

            val configSlot = slot<BacktestConfig>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = capture(configSlot),
                    stats = any(),
                    finalCapital = any(),
                    maxDrawdown = any(),
                    peakBalance = any(),
                    buyAndHoldReturn = any(),
                    startDate = any(),
                    endDate = any(),
                )
            } returns createEmptyBacktestResult(config)

            engine.runBacktest(config, candles)

            assertThat(configSlot.captured).isEqualTo(config)
        }
    }
}
