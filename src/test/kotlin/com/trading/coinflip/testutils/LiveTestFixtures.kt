package com.trading.coinflip.testutils

import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.live.model.LiveSessionEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

object LiveTestFixtures {
    const val TEST_SYMBOL = "BTCUSDT"
    val TEST_TIMEFRAME = Timeframe.ONE_HOUR
    val DEFAULT_INITIAL_CAPITAL: BigDecimal = BigDecimal("10000")
    val DEFAULT_ATR: BigDecimal = BigDecimal("1000")

    fun createCandle(
        symbol: String = TEST_SYMBOL,
        timeframe: Timeframe = TEST_TIMEFRAME,
        openTime: Instant = Instant.parse("2024-01-01T00:00:00Z"),
        open: BigDecimal = BigDecimal("50000"),
        high: BigDecimal = BigDecimal("51000"),
        low: BigDecimal = BigDecimal("49000"),
        close: BigDecimal = BigDecimal("50500"),
        volume: BigDecimal = BigDecimal("1000"),
        atr: BigDecimal? = DEFAULT_ATR,
        id: Long? = null,
    ): CandleEntity =
        CandleEntity(
            id = id,
            symbol = symbol,
            timeframe = timeframe,
            openTime = openTime,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume,
            atr = atr,
        )

    fun createCandleSequence(
        symbol: String = TEST_SYMBOL,
        timeframe: Timeframe = TEST_TIMEFRAME,
        count: Int,
        startTime: Instant = Instant.parse("2024-01-01T00:00:00Z"),
        startPrice: BigDecimal = BigDecimal("50000"),
        priceChange: BigDecimal = BigDecimal("100"),
        atr: BigDecimal = DEFAULT_ATR,
    ): List<CandleEntity> =
        (0 until count).map { i ->
            val currentPrice = startPrice + (priceChange * BigDecimal(i))
            val openTime = startTime.plus(i.toLong(), ChronoUnit.HOURS)
            createCandle(
                symbol = symbol,
                timeframe = timeframe,
                openTime = openTime,
                open = currentPrice,
                high = currentPrice + BigDecimal("500"),
                low = currentPrice - BigDecimal("500"),
                close = currentPrice + priceChange,
                atr = atr,
            )
        }

    fun createLiveSession(
        id: Long? = null,
        symbol: String = TEST_SYMBOL,
        initialCapital: BigDecimal = DEFAULT_INITIAL_CAPITAL,
    ): LiveSessionEntity =
        LiveSessionEntity(
            id = id,
            symbol = symbol,
            timeframe = TEST_TIMEFRAME,
            initialCapital = initialCapital,
            currentBalance = initialCapital,
            peakBalance = initialCapital,
        )
}
