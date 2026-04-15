package com.trading.coinflip.data

import com.trading.coinflip.common.model.Timeframe
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CandleRepository : JpaRepository<CandleEntity, Long> {
    fun findBySymbolAndTimeframeOrderByOpenTimeAsc(
        symbol: String,
        timeframe: Timeframe,
    ): List<CandleEntity>

    fun findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
        endTime: Instant,
    ): List<CandleEntity>

    @Query("SELECT MIN(c.openTime) FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    fun findEarliestCandleTime(
        symbol: String,
        timeframe: Timeframe,
    ): Instant?

    @Query("SELECT MAX(c.openTime) FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    fun findLatestCandleTime(
        symbol: String,
        timeframe: Timeframe,
    ): Instant?

    // Count queries (avoid loading all entities just to count)
    @Query("SELECT COUNT(c) FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    fun countBySymbolAndTimeframe(
        symbol: String,
        timeframe: Timeframe,
    ): Long

    @Query(
        "SELECT COUNT(c) FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.openTime >= :startTime",
    )
    fun countBySymbolAndTimeframeFromDate(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
    ): Long

    // Projection query for deduplication (only load openTime, not full entities)
    @Query("SELECT c.openTime FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    fun findOpenTimesBySymbolAndTimeframe(
        symbol: String,
        timeframe: Timeframe,
    ): List<Instant>

    // For incremental ATR calculation
    @Query(
        "SELECT c FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.atr IS NOT NULL ORDER BY c.openTime DESC LIMIT 1",
    )
    fun findLastCandleWithATR(
        symbol: String,
        timeframe: Timeframe,
    ): CandleEntity?

    @Query(
        "SELECT c FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.atr IS NULL ORDER BY c.openTime ASC",
    )
    fun findCandlesWithoutATR(
        symbol: String,
        timeframe: Timeframe,
    ): List<CandleEntity>
}
