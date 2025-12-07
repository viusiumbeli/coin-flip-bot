package com.trading.coinflip.data

import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.candle.CandleRepository
import com.trading.coinflip.common.model.Timeframe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Integration tests for ATR calculation by PostgreSQL BEFORE INSERT trigger.
 *
 * ATR Formula:
 * - True Range (TR) = max(high - low, |high - prevClose|, |low - prevClose|)
 * - First candle TR = high - low (no previous close)
 * - ATR for candles 1-9: NULL (not enough data, period=10)
 * - ATR for candle 10: SMA of first 10 True Ranges
 * - ATR for candle 11+: EMA = prevATR + (1/period) * (TR - prevATR)
 */
@SpringBootTest
@ActiveProfiles("test")
class ATRTriggerIntegrationTest {
    @Autowired
    lateinit var candleRepository: CandleRepository

    companion object {
        private const val TEST_SYMBOL = "ATRTEST"
        private val TEST_TIMEFRAME = Timeframe.ONE_HOUR
        private val BASE_TIME = Instant.parse("2024-01-01T00:00:00Z")
        private const val ATR_PERIOD = 10
    }

    @BeforeEach
    fun cleanup() =
        runBlocking {
            // Delete only test data to avoid affecting other tests
            candleRepository.deleteBySymbolAndTimeframe(TEST_SYMBOL, TEST_TIMEFRAME)
        }

    // --- Helper Methods ---

    /**
     * Creates a candle with predictable values.
     * Default spread of 20 means: high = price + 10, low = price - 10
     * So True Range (high - low) = 20 for all candles when no gaps
     */
    private fun createTestCandle(
        index: Int,
        basePrice: BigDecimal = BigDecimal("100"),
        spread: BigDecimal = BigDecimal("20"),
        atr: BigDecimal? = null,
    ): CandleEntity {
        val price = basePrice + (BigDecimal(index) * BigDecimal("10"))
        return CandleEntity(
            symbol = TEST_SYMBOL,
            timeframe = TEST_TIMEFRAME,
            openTime = BASE_TIME.plus(index.toLong(), ChronoUnit.HOURS),
            open = price,
            high = price + spread.divide(BigDecimal("2")),
            low = price - spread.divide(BigDecimal("2")),
            close = price,
            volume = BigDecimal("1000"),
            atr = atr,
        )
    }

    /**
     * Creates a candle with explicit OHLC values for testing specific scenarios.
     */
    private fun createCustomCandle(
        index: Int,
        open: BigDecimal,
        high: BigDecimal,
        low: BigDecimal,
        close: BigDecimal,
        atr: BigDecimal? = null,
    ): CandleEntity =
        CandleEntity(
            symbol = TEST_SYMBOL,
            timeframe = TEST_TIMEFRAME,
            openTime = BASE_TIME.plus(index.toLong(), ChronoUnit.HOURS),
            open = open,
            high = high,
            low = low,
            close = close,
            volume = BigDecimal("1000"),
            atr = atr,
        )

    /**
     * Saves candle and re-fetches to get trigger-calculated ATR.
     */
    private suspend fun saveAndFetch(candle: CandleEntity): CandleEntity {
        candleRepository.save(candle)
        return candleRepository.findBySymbolAndTimeframeAndOpenTime(
            candle.symbol,
            candle.timeframe,
            candle.openTime,
        )!!
    }

    // --- Test A: First 9 Candles Have NULL ATR ---

    @Test
    @DisplayName("A1: First 9 candles have null ATR (not enough history for period=10)")
    fun firstNineCandlesHaveNullATR() =
        runBlocking {
            // Insert 9 candles one by one
            for (i in 0 until 9) {
                val candle = createTestCandle(i)
                val saved = saveAndFetch(candle)

                assertThat(saved.atr)
                    .describedAs("Candle ${i + 1} should have null ATR")
                    .isNull()
            }
        }

    // --- Test B: 10th Candle Has ATR as SMA ---

    @Test
    @DisplayName("B1: 10th candle has ATR calculated as SMA of first 10 True Ranges")
    fun tenthCandleHasATRasSMA(): Unit =
        runBlocking {
            // Insert 10 candles with constant spread = 20, so TR = 20 for all
            // Each candle's close = price, next candle's price = prev + 10
            // So: high - prevClose = (price + 10) - prevPrice = 10 + 10 = 20
            //     low - prevClose = (price - 10) - prevPrice = -10 + 10 = 0
            // TR = max(20, 20, 0) = 20
            for (i in 0 until 10) {
                val candle = createTestCandle(i)
                saveAndFetch(candle)
            }

            // Fetch the 10th candle (index 9)
            val tenthCandle =
                candleRepository.findBySymbolAndTimeframeAndOpenTime(
                    TEST_SYMBOL,
                    TEST_TIMEFRAME,
                    BASE_TIME.plus(9, ChronoUnit.HOURS),
                )!!

            // SMA of 10 TRs where each TR = 20 → ATR = 20
            assertThat(tenthCandle.atr)
                .describedAs("10th candle ATR should be SMA = 20")
                .isEqualByComparingTo(BigDecimal("20.00000000"))
        }

    // --- Test C: 11th+ Candles Use EMA Formula ---

    @Test
    @DisplayName("C1: 11th candle uses EMA formula with same TR maintains ATR")
    fun eleventhCandleUsesEMAWithSameTR(): Unit =
        runBlocking {
            // Insert 11 candles with constant TR = 20
            for (i in 0 until 11) {
                val candle = createTestCandle(i)
                saveAndFetch(candle)
            }

            val eleventhCandle =
                candleRepository.findBySymbolAndTimeframeAndOpenTime(
                    TEST_SYMBOL,
                    TEST_TIMEFRAME,
                    BASE_TIME.plus(10, ChronoUnit.HOURS),
                )!!

            // EMA: prevATR + (1/10) * (TR - prevATR) = 20 + 0.1 * (20 - 20) = 20
            assertThat(eleventhCandle.atr)
                .describedAs("11th candle ATR should remain 20 with same TR")
                .isEqualByComparingTo(BigDecimal("20.00000000"))
        }

    @Test
    @DisplayName("C2: EMA formula correctly updates ATR when TR changes")
    fun emaFormulaUpdatesATRWhenTRChanges(): Unit =
        runBlocking {
            // Insert 10 candles with TR = 20
            for (i in 0 until 10) {
                val candle = createTestCandle(i)
                saveAndFetch(candle)
            }

            // 11th candle with larger spread = 40, so TR = 40
            val largeSpreadCandle = createTestCandle(10, spread = BigDecimal("40"))
            val saved = saveAndFetch(largeSpreadCandle)

            // EMA: 20 + (1/10) * (40 - 20) = 20 + 2 = 22
            assertThat(saved.atr)
                .describedAs("ATR should increase when TR > prevATR")
                .isEqualByComparingTo(BigDecimal("22.00000000"))
        }

    @Test
    @DisplayName("C3: Multiple EMA iterations produce correct values")
    fun multipleEMAIterations(): Unit =
        runBlocking {
            // Insert 10 candles with TR = 20 → ATR(10) = 20
            for (i in 0 until 10) {
                saveAndFetch(createTestCandle(i))
            }

            // 11th candle with TR = 40 → ATR(11) = 20 + 0.1*(40-20) = 22
            saveAndFetch(createTestCandle(10, spread = BigDecimal("40")))

            // 12th candle with TR = 20 → ATR(12) = 22 + 0.1*(20-22) = 21.8
            saveAndFetch(createTestCandle(11))

            val candle12 =
                candleRepository.findBySymbolAndTimeframeAndOpenTime(
                    TEST_SYMBOL,
                    TEST_TIMEFRAME,
                    BASE_TIME.plus(11, ChronoUnit.HOURS),
                )!!

            assertThat(candle12.atr)
                .describedAs("ATR should decrease back towards TR when TR < prevATR")
                .isEqualByComparingTo(BigDecimal("21.80000000"))
        }

    // --- Test D: True Range Calculation Variations ---

    @Test
    @DisplayName("D1: True Range for first candle uses high-low only")
    fun trueRangeFirstCandleUsesHighLow(): Unit =
        runBlocking {
            // Insert first candle with specific high-low spread
            val candle =
                createCustomCandle(
                    index = 0,
                    open = BigDecimal("100"),
                    high = BigDecimal("130"),
                    low = BigDecimal("90"),
                    close = BigDecimal("110"),
                )
            saveAndFetch(candle)

            // TR = 130 - 90 = 40, but we can't see TR directly
            // Insert 9 more candles with TR = 40 to verify ATR calculation
            for (i in 1 until 10) {
                val price = BigDecimal("100") + (BigDecimal(i) * BigDecimal("10"))
                saveAndFetch(
                    createCustomCandle(
                        index = i,
                        open = price,
                        high = price + BigDecimal("20"),
                        low = price - BigDecimal("20"),
                        close = price,
                    ),
                )
            }

            val tenthCandle =
                candleRepository.findBySymbolAndTimeframeAndOpenTime(
                    TEST_SYMBOL,
                    TEST_TIMEFRAME,
                    BASE_TIME.plus(9, ChronoUnit.HOURS),
                )!!

            // First candle TR = 40, rest TR = 40, so SMA = 40
            assertThat(tenthCandle.atr)
                .describedAs("ATR should reflect first candle's high-low TR")
                .isEqualByComparingTo(BigDecimal("40.00000000"))
        }

    @Test
    @DisplayName("D2: True Range uses gap-up formula when |high - prevClose| > high - low")
    fun trueRangeUsesGapUpFormula(): Unit =
        runBlocking {
            // Insert 9 candles with TR = 20
            for (i in 0 until 9) {
                saveAndFetch(createTestCandle(i))
            }

            // 10th candle (index 9) gaps up significantly
            // Previous close = 100 + 8*10 = 180 (from candle at index 8)
            // Gap up to open at 250, high=260, low=240, close=250
            // TR = max(260-240, |260-180|, |240-180|) = max(20, 80, 60) = 80
            val gapUpCandle =
                createCustomCandle(
                    index = 9,
                    open = BigDecimal("250"),
                    high = BigDecimal("260"),
                    low = BigDecimal("240"),
                    close = BigDecimal("250"),
                )
            val saved = saveAndFetch(gapUpCandle)

            // SMA of TRs: 9 candles with TR=20 plus gap candle with TR=80
            // Sum = 9*20 + 80 = 260, SMA = 260/10 = 26
            assertThat(saved.atr)
                .describedAs("ATR should include gap-up in TR calculation")
                .isEqualByComparingTo(BigDecimal("26.00000000"))
        }

    @Test
    @DisplayName("D3: True Range uses gap-down formula when |low - prevClose| > high - low")
    fun trueRangeUsesGapDownFormula(): Unit =
        runBlocking {
            // Insert 9 candles with TR = 20
            for (i in 0 until 9) {
                saveAndFetch(createTestCandle(i))
            }

            // 10th candle (index 9) gaps down significantly
            // Previous close = 180 (from candle at index 8)
            // Gap down to open at 100, high=110, low=100, close=105
            // TR = max(110-100, |110-180|, |100-180|) = max(10, 70, 80) = 80
            val gapDownCandle =
                createCustomCandle(
                    index = 9,
                    open = BigDecimal("100"),
                    high = BigDecimal("110"),
                    low = BigDecimal("100"),
                    close = BigDecimal("105"),
                )
            val saved = saveAndFetch(gapDownCandle)

            // SMA of TRs: 9 candles with TR=20 plus gap candle with TR=80
            // Sum = 9*20 + 80 = 260, SMA = 260/10 = 26
            assertThat(saved.atr)
                .describedAs("ATR should include gap-down in TR calculation")
                .isEqualByComparingTo(BigDecimal("26.00000000"))
        }

    // --- Test E: Batch Insert ---

    @Test
    @DisplayName("E1: Batch insert via saveAll calculates ATR correctly for all candles")
    fun batchInsertCalculatesATRCorrectly(): Unit =
        runBlocking {
            // Create 12 candles with TR = 20
            val candles = (0 until 12).map { createTestCandle(it) }

            // Save all at once
            candleRepository.saveAll(candles).toList()

            // Fetch all and verify ATR
            val savedCandles =
                candleRepository
                    .findCandlesPageByOffset(
                        symbol = TEST_SYMBOL,
                        timeframe = TEST_TIMEFRAME,
                        startTime = BASE_TIME,
                        endTime = BASE_TIME.plus(100, ChronoUnit.HOURS),
                        limit = 100,
                        offset = 0,
                    ).toList()

            assertThat(savedCandles).hasSize(12)

            // First 9 should have null ATR
            savedCandles.take(9).forEachIndexed { i, candle ->
                assertThat(candle.atr)
                    .describedAs("Candle ${i + 1} should have null ATR")
                    .isNull()
            }

            // 10th should have SMA = 20
            assertThat(savedCandles[9].atr)
                .describedAs("10th candle should have ATR = 20")
                .isEqualByComparingTo(BigDecimal("20.00000000"))

            // 11th and 12th should have EMA = 20 (same TR)
            assertThat(savedCandles[10].atr)
                .describedAs("11th candle should have ATR = 20")
                .isEqualByComparingTo(BigDecimal("20.00000000"))

            assertThat(savedCandles[11].atr)
                .describedAs("12th candle should have ATR = 20")
                .isEqualByComparingTo(BigDecimal("20.00000000"))
        }

    // --- Test F: Skip Preset ATR ---

    @Test
    @DisplayName("F1: Trigger skips calculation if ATR is already set")
    fun triggerSkipsPresetATR(): Unit =
        runBlocking {
            // Insert 9 candles first
            for (i in 0 until 9) {
                saveAndFetch(createTestCandle(i))
            }

            // Insert 10th candle with preset ATR
            val presetATR = BigDecimal("99.99999999")
            val candle = createTestCandle(9, atr = presetATR)
            val saved = saveAndFetch(candle)

            // Trigger should preserve the preset value
            assertThat(saved.atr)
                .describedAs("Trigger should not overwrite preset ATR")
                .isEqualByComparingTo(presetATR)
        }

    // --- Test: Symbol/Timeframe Isolation ---

    @Test
    @DisplayName("G1: ATR calculation is isolated per symbol/timeframe")
    fun atrCalculationIsIsolatedPerSymbolTimeframe() =
        runBlocking {
            // Insert candles for first symbol
            for (i in 0 until 10) {
                val candle =
                    CandleEntity(
                        symbol = "SYMBOL_A",
                        timeframe = TEST_TIMEFRAME,
                        openTime = BASE_TIME.plus(i.toLong(), ChronoUnit.HOURS),
                        open = BigDecimal("100"),
                        high = BigDecimal("110"),
                        low = BigDecimal("90"),
                        close = BigDecimal("100"),
                        volume = BigDecimal("1000"),
                    )
                candleRepository.save(candle)
            }

            // Insert single candle for second symbol
            val differentSymbolCandle =
                CandleEntity(
                    symbol = "SYMBOL_B",
                    timeframe = TEST_TIMEFRAME,
                    openTime = BASE_TIME,
                    open = BigDecimal("100"),
                    high = BigDecimal("110"),
                    low = BigDecimal("90"),
                    close = BigDecimal("100"),
                    volume = BigDecimal("1000"),
                )
            candleRepository.save(differentSymbolCandle)

            val fetchedB =
                candleRepository.findBySymbolAndTimeframeAndOpenTime(
                    "SYMBOL_B",
                    TEST_TIMEFRAME,
                    BASE_TIME,
                )!!

            // SYMBOL_B should have null ATR (only 1 candle, not influenced by SYMBOL_A)
            assertThat(fetchedB.atr)
                .describedAs("Different symbol should not affect ATR calculation")
                .isNull()

            // Clean up other symbols
            candleRepository.deleteBySymbolAndTimeframe("SYMBOL_A", TEST_TIMEFRAME)
            candleRepository.deleteBySymbolAndTimeframe("SYMBOL_B", TEST_TIMEFRAME)
        }
}
