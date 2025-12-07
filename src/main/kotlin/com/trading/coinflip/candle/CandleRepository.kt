package com.trading.coinflip.candle

import com.trading.coinflip.common.model.Timeframe
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CandleRepository : CoroutineCrudRepository<CandleEntity, Long> {
    @Query(
        """
        SELECT * FROM candles
        WHERE symbol = :symbol AND timeframe = :timeframe
        ORDER BY open_time DESC LIMIT 1
        """,
    )
    suspend fun findLatestCandle(
        symbol: String,
        timeframe: Timeframe,
    ): CandleEntity?

    @Query(
        """
        SELECT COUNT(*) FROM candles
        WHERE symbol = :symbol AND timeframe = :timeframe
        AND open_time >= :startTime AND open_time <= :endTime
        """,
    )
    suspend fun countCandlesInRange(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
        endTime: Instant,
    ): Long

    @Query(
        """
        SELECT * FROM candles
        WHERE symbol = :symbol AND timeframe = :timeframe AND atr IS NOT NULL
        ORDER BY open_time DESC LIMIT 1
        """,
    )
    suspend fun findLastCandleWithATR(
        symbol: String,
        timeframe: Timeframe,
    ): CandleEntity?

    @Query(
        """
        SELECT * FROM candles
        WHERE symbol = :symbol AND timeframe = :timeframe AND open_time = :openTime
        LIMIT 1
        """,
    )
    suspend fun findBySymbolAndTimeframeAndOpenTime(
        symbol: String,
        timeframe: Timeframe,
        openTime: Instant,
    ): CandleEntity?

    @Query(
        """
        SELECT * FROM candles
        WHERE symbol = :symbol AND timeframe = :timeframe
        AND open_time >= :startTime AND open_time <= :endTime
        ORDER BY open_time ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun findCandlesPageByOffset(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
        endTime: Instant,
        limit: Int,
        offset: Long,
    ): Flow<CandleEntity>

    suspend fun deleteBySymbolAndTimeframe(
        symbol: String,
        timeframe: Timeframe,
    )
}
