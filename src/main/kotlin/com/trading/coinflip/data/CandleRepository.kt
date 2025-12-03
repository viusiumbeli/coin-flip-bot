package com.trading.coinflip.data

import com.trading.coinflip.common.model.Timeframe
import kotlinx.coroutines.flow.Flow
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

// Projection class for scalar open_time queries
data class OpenTimeProjection(
    @Id
    @Column("open_time")
    val openTime: Instant,
)

@Repository
interface CandleRepository : CoroutineCrudRepository<CandleEntity, Long> {
    fun findBySymbolAndTimeframeOrderByOpenTimeAsc(
        symbol: String,
        timeframe: Timeframe,
    ): Flow<CandleEntity>

    @Query(
        """
        SELECT * FROM candles
        WHERE symbol = :symbol AND timeframe = :timeframe
        AND open_time BETWEEN :startTime AND :endTime
        ORDER BY open_time ASC
        """,
    )
    fun findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
        endTime: Instant,
    ): Flow<CandleEntity>

    @Query(
        """
        SELECT * FROM candles
        WHERE symbol = :symbol AND timeframe = :timeframe
        ORDER BY open_time ASC LIMIT 1
        """,
    )
    suspend fun findEarliestCandle(
        symbol: String,
        timeframe: Timeframe,
    ): CandleEntity?

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

    @Query("SELECT COUNT(*) FROM candles WHERE symbol = :symbol AND timeframe = :timeframe")
    suspend fun countBySymbolAndTimeframe(
        symbol: String,
        timeframe: Timeframe,
    ): Long

    @Query(
        "SELECT COUNT(*) FROM candles WHERE symbol = :symbol AND timeframe = :timeframe AND open_time >= :startTime",
    )
    suspend fun countBySymbolAndTimeframeFromDate(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
    ): Long

    @Query("SELECT open_time FROM candles WHERE symbol = :symbol AND timeframe = :timeframe")
    fun findOpenTimesBySymbolAndTimeframe(
        symbol: String,
        timeframe: Timeframe,
    ): Flow<OpenTimeProjection>

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
        WHERE symbol = :symbol AND timeframe = :timeframe AND atr IS NULL
        ORDER BY open_time ASC
        """,
    )
    fun findCandlesWithoutATR(
        symbol: String,
        timeframe: Timeframe,
    ): Flow<CandleEntity>

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
}
