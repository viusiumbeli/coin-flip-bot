package com.trading.coinflip.data

import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.Timeframe
import com.trading.coinflip.strategy.ATRCalculator
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class DataService(
    private val candleRepository: CandleRepository,
    private val binanceClient: BinanceClient,
    private val atrCalculator: ATRCalculator
) {

    @Transactional
    fun loadHistoricalData(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
        forceReload: Boolean = false
    ) = runBlocking {
        if (!forceReload) {
            val existingCount = candleRepository.findBySymbolAndTimeframeFromDate(
                symbol, timeframe, startDate
            ).size

            if (existingCount > 0) {
                log.info { "Found $existingCount existing candles for $symbol $timeframe, skipping download" }
                return@runBlocking
            }
        }

        log.info { "Loading historical data for $symbol $timeframe from $startDate" }
        val candles = binanceClient.fetchAllHistoricalData(symbol, timeframe, startDate)

        if (candles.isEmpty()) {
            log.warn { "No candles fetched for $symbol $timeframe" }
            return@runBlocking
        }

        // Save candles
        val savedCandles = mutableListOf<Candle>()
        candles.forEach { candle ->
            if (!candleRepository.existsBySymbolAndTimeframeAndOpenTime(
                    symbol, timeframe, candle.openTime
                )
            ) {
                savedCandles.add(candleRepository.save(candle))
            }
        }

        log.info { "Saved ${savedCandles.size} new candles for $symbol $timeframe" }

        // Calculate and update ATR
        calculateAndSaveATR(symbol, timeframe)
    }

    @Transactional
    fun calculateAndSaveATR(symbol: String, timeframe: Timeframe, period: Int = 10) {
        log.info { "Calculating ATR for $symbol $timeframe with period $period" }

        val candles = candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe)
        if (candles.size < period) {
            log.warn { "Not enough candles to calculate ATR. Need at least $period, got ${candles.size}" }
            return
        }

        val candlesWithATR = atrCalculator.calculateATR(candles, period)

        // Update candles with ATR values
        var updated = 0
        candlesWithATR.forEach { candle ->
            if (candle.atr != null && candle.id != null) {
                val existing = candleRepository.findById(candle.id).orElse(null)
                if (existing != null) {
                    existing.atr = candle.atr
                    candleRepository.save(existing)
                    updated++
                }
            }
        }

        log.info { "Updated $updated candles with ATR values" }
    }

    fun getCandlesForBacktest(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant? = null,
        endDate: Instant? = null
    ): List<Candle> {
        return if (startDate != null && endDate != null) {
            candleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                symbol, timeframe, startDate, endDate
            )
        } else if (startDate != null) {
            candleRepository.findBySymbolAndTimeframeFromDate(symbol, timeframe, startDate)
        } else {
            candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe)
        }
    }

    fun getDataSummary(symbol: String, timeframe: Timeframe): String {
        val earliest = candleRepository.findEarliestCandleTime(symbol, timeframe)
        val latest = candleRepository.findLatestCandleTime(symbol, timeframe)
        val count = candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe).size

        return """
            Symbol: $symbol
            Timeframe: ${timeframe.label}
            Candles: $count
            Period: $earliest to $latest
        """.trimIndent()
    }
}
