package com.trading.coinflip.backtest

import com.trading.coinflip.analytics.PerformanceAnalytics
import com.trading.coinflip.backtest.model.BacktestConfig
import com.trading.coinflip.backtest.model.BacktestResult
import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.config.TradingConfig
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.TradingProcessor
import com.trading.coinflip.engine.model.MutableTradingState
import com.trading.coinflip.engine.model.Position
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.Trade
import com.trading.coinflip.engine.model.TradingEvent
import com.trading.coinflip.engine.model.TradingStateView
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

    private fun createState(config: BacktestConfig): MutableTradingState =
        MutableTradingState.create(config.initialCapital, config.collectTrades)

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

    private fun mockAnalytics(result: BacktestResult) {
        every {
            analytics.calculatePerformance(
                config = any(),
                state = any(),
                candles = any(),
            )
        } returns result
    }

    @Nested
    @DisplayName("Empty Candles")
    inner class EmptyCandlesTests {
        @Test
        @DisplayName("returns empty result when candles list is empty")
        fun emptyCandles_returnsEmptyResult() {
            val config = createTestConfig()
            val state = createState(config)

            val result = engine.runBacktest(state, config, emptyList())

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
            val state = createState(config)

            engine.runBacktest(state, config, emptyList())

            verify(exactly = 0) { processor.processCandle(any(), any()) }
            verify(exactly = 0) { analytics.calculatePerformance(any(), any(), any()) }
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
            mockAnalytics(expectedResult)

            val state = createState(config)
            engine.runBacktest(state, config, listOf(candle))

            verify(exactly = 1) { processor.processCandle(any(), candle) }
        }

        @Test
        @DisplayName("passes candles to analytics")
        fun singleCandle_passesCandlesToAnalytics() {
            val config = createTestConfig()
            val candle = createTestCandle(0)
            val candlesSlot = slot<List<CandleEntity>>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    state = any(),
                    candles = capture(candlesSlot),
                )
            } returns createEmptyBacktestResult(config)

            val state = createState(config)
            engine.runBacktest(state, config, listOf(candle))

            assertThat(candlesSlot.captured).hasSize(1)
            assertThat(candlesSlot.captured[0]).isEqualTo(candle)
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
            mockAnalytics(expectedResult)

            val state = createState(config)
            engine.runBacktest(state, config, candles)

            verify(exactly = 5) { processor.processCandle(any(), any()) }
        }

        @Test
        @DisplayName("passes all candles to analytics")
        fun multipleCandlesNoTrades_passesAllCandlesToAnalytics() {
            val config = createTestConfig()
            val candles = (0 until 3).map { createTestCandle(it) }
            val candlesSlot = slot<List<CandleEntity>>()

            every { processor.processCandle(any(), any()) } returns emptyList()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    state = any(),
                    candles = capture(candlesSlot),
                )
            } returns createEmptyBacktestResult(config)

            val state = createState(config)
            engine.runBacktest(state, config, candles)

            assertThat(candlesSlot.captured).hasSize(3)
            assertThat(candlesSlot.captured).isEqualTo(candles)
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
            mockAnalytics(createEmptyBacktestResult(config))

            val state = createState(config)
            engine.runBacktest(state, config, candles)

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
        @DisplayName("passes state to analytics after applying events")
        fun closedTrades_statePassedToAnalytics() {
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

            val stateSlot = slot<TradingStateView>()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    state = capture(stateSlot),
                    candles = any(),
                )
            } returns createEmptyBacktestResult(config)

            val state = createState(config)
            engine.runBacktest(state, config, candles)

            // State should have the trade after events applied
            assertThat(stateSlot.captured.closedTrades).hasSize(1)
            assertThat(stateSlot.captured.closedTrades[0]).isEqualTo(trade)
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

            mockAnalytics(createEmptyBacktestResult(config))

            val state = createState(config)
            engine.runBacktest(state, config, candles)

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

            mockAnalytics(createEmptyBacktestResult(config))

            val state = createState(config)
            engine.runBacktest(state, config, candles)

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

            mockAnalytics(createEmptyBacktestResult(config))

            val state = createState(config)
            engine.runBacktest(state, config, candles)

            assertThat(exitReasonSlot.captured).isEqualTo("End of backtest period")
        }
    }

    @Nested
    @DisplayName("State Tracking")
    inner class StateTrackingTests {
        @Test
        @DisplayName("passes state with updated balance to analytics")
        fun passesUpdatedStateToAnalytics() {
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

            val stateSlot = slot<TradingStateView>()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    state = capture(stateSlot),
                    candles = any(),
                )
            } returns createEmptyBacktestResult(config)

            val state = createState(config)
            engine.runBacktest(state, config, candles)

            assertThat(stateSlot.captured.accountBalance).isEqualByComparingTo(finalBalance)
        }

        @Test
        @DisplayName("passes state with max drawdown to analytics")
        fun passesStateWithMaxDrawdown() {
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

            val stateSlot = slot<TradingStateView>()
            every {
                analytics.calculatePerformance(
                    config = any(),
                    state = capture(stateSlot),
                    candles = any(),
                )
            } returns createEmptyBacktestResult(config)

            val state = createState(config)
            engine.runBacktest(state, config, candles)

            assertThat(stateSlot.captured.maxDrawdown).isEqualByComparingTo(maxDrawdown)
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
                    state = any(),
                    candles = any(),
                )
            } returns createEmptyBacktestResult(config)

            val state = createState(config)
            engine.runBacktest(state, config, candles)

            assertThat(configSlot.captured).isEqualTo(config)
        }
    }

    @Nested
    @DisplayName("Analytics Result")
    inner class AnalyticsResultTests {
        @Test
        @DisplayName("returns result from analytics")
        fun returnsAnalyticsResult() {
            val config = createTestConfig()
            val candles = listOf(createTestCandle(0))
            val expectedResult =
                createEmptyBacktestResult(config).copy(
                    totalTrades = 42,
                    winRate = BigDecimal("65.00"),
                )

            every { processor.processCandle(any(), any()) } returns emptyList()
            mockAnalytics(expectedResult)

            val state = createState(config)
            val result = engine.runBacktest(state, config, candles)

            assertThat(result).isEqualTo(expectedResult)
            assertThat(result.totalTrades).isEqualTo(42)
            assertThat(result.winRate).isEqualByComparingTo(BigDecimal("65.00"))
        }
    }
}
