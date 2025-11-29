package com.trading.coinflip.data

import com.trading.coinflip.common.model.CandleEntity
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

    @Query(
        "SELECT c FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.openTime >= :startTime ORDER BY c.openTime ASC",
    )
    fun findBySymbolAndTimeframeFromDate(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
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

    @Query("SELECT COUNT(c) FROM CandleEntity c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.atr IS NULL")
    fun countCandlesWithoutATR(
        symbol: String,
        timeframe: Timeframe,
    ): Long
}
