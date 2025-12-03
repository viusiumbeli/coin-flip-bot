package com.trading.coinflip.testutils

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.model.Position
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.TradingState
import com.trading.coinflip.live.model.LivePositionEntity
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

    fun createRisingCandles(
        count: Int,
        startTime: Instant = Instant.parse("2024-01-01T00:00:00Z"),
        startPrice: BigDecimal = BigDecimal("50000"),
        risePerCandle: BigDecimal = BigDecimal("500"),
    ): List<CandleEntity> =
        createCandleSequence(
            count = count,
            startTime = startTime,
            startPrice = startPrice,
            priceChange = risePerCandle,
        )

    fun createFallingCandles(
        count: Int,
        startTime: Instant = Instant.parse("2024-01-01T00:00:00Z"),
        startPrice: BigDecimal = BigDecimal("50000"),
        fallPerCandle: BigDecimal = BigDecimal("500"),
    ): List<CandleEntity> =
        createCandleSequence(
            count = count,
            startTime = startTime,
            startPrice = startPrice,
            priceChange = -fallPerCandle,
        )

    fun createStopLossTriggerCandle(
        position: Position,
        openTime: Instant = Instant.now(),
    ): CandleEntity {
        val stopPrice = position.trailingStop
        return when (position.side) {
            PositionSide.LONG ->
                createCandle(
                    openTime = openTime,
                    open = stopPrice + BigDecimal("100"),
                    high = stopPrice + BigDecimal("100"),
                    low = stopPrice - BigDecimal("100"),
                    close = stopPrice - BigDecimal("50"),
                )
            PositionSide.SHORT ->
                createCandle(
                    openTime = openTime,
                    open = stopPrice - BigDecimal("100"),
                    high = stopPrice + BigDecimal("100"),
                    low = stopPrice - BigDecimal("100"),
                    close = stopPrice + BigDecimal("50"),
                )
        }
    }

    fun createTradingState(
        initialCapital: BigDecimal = DEFAULT_INITIAL_CAPITAL,
        accountBalance: BigDecimal = initialCapital,
        openPositions: List<Position> = emptyList(),
    ): TradingState =
        TradingState(
            accountBalance = accountBalance,
            peakBalance = maxOf(initialCapital, accountBalance),
            maxDrawdown = BigDecimal.ZERO,
            openPositions = openPositions,
            closedTrades = emptyList(),
            tradeIdCounter = 0L,
            positionIdCounter = openPositions.size.toLong(),
        )

    fun createPosition(
        id: Long = 1L,
        symbol: String = TEST_SYMBOL,
        timeframe: Timeframe = TEST_TIMEFRAME,
        side: PositionSide = PositionSide.LONG,
        entryTime: Instant = Instant.parse("2024-01-01T00:00:00Z"),
        entryPrice: BigDecimal = BigDecimal("50000"),
        positionSize: BigDecimal = BigDecimal("0.02"),
        atrMultiplier: BigDecimal = BigDecimal("3.0"),
        atr: BigDecimal = DEFAULT_ATR,
        balanceBeforeOpen: BigDecimal = DEFAULT_INITIAL_CAPITAL,
    ): Position {
        val stopDistance = atr * atrMultiplier
        val initialStopLoss =
            when (side) {
                PositionSide.LONG -> entryPrice - stopDistance
                PositionSide.SHORT -> entryPrice + stopDistance
            }
        val allocatedCapital = entryPrice * positionSize

        return Position(
            id = id,
            symbol = symbol,
            timeframe = timeframe,
            side = side,
            entryTime = entryTime,
            entryPrice = entryPrice,
            positionSize = positionSize,
            initialStopLoss = initialStopLoss,
            trailingStop = initialStopLoss,
            highestFavorablePrice = entryPrice,
            status = PositionStatus.OPEN,
            balanceBeforeOpen = balanceBeforeOpen,
            balanceAfterOpen = balanceBeforeOpen,
            allocatedCapital = allocatedCapital,
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

    fun createLivePositionEntity(
        position: Position,
        sessionId: Long,
    ): LivePositionEntity = LivePositionEntity.fromPosition(position, sessionId)
}
