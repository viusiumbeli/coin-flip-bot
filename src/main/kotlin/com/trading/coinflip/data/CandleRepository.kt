package com.trading.coinflip.data

import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.Timeframe
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CandleRepository : JpaRepository<Candle, Long> {

    fun findBySymbolAndTimeframeOrderByOpenTimeAsc(
        symbol: String,
        timeframe: Timeframe
    ): List<Candle>

    fun findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
        endTime: Instant
    ): List<Candle>

    @Query("SELECT c FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.openTime >= :startTime ORDER BY c.openTime ASC")
    fun findBySymbolAndTimeframeFromDate(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant
    ): List<Candle>

    fun existsBySymbolAndTimeframeAndOpenTime(
        symbol: String,
        timeframe: Timeframe,
        openTime: Instant
    ): Boolean

    @Query("SELECT MIN(c.openTime) FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    fun findEarliestCandleTime(symbol: String, timeframe: Timeframe): Instant?

    @Query("SELECT MAX(c.openTime) FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    fun findLatestCandleTime(symbol: String, timeframe: Timeframe): Instant?
}
