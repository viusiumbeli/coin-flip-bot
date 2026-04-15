package com.trading.coinflip.data

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DataService(
    private val candleRepository: CandleRepository,
    private val binanceClient: BinanceClient,
    private val candlePersistenceService: CandlePersistenceService,
    private val properties: BacktestProperties,
) {
    private val log = KotlinLogging.logger {}

    suspend fun loadHistoricalData(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
    ) {
        val existingCount = candleRepository.countBySymbolAndTimeframeFromDate(symbol, timeframe, startDate)
        if (existingCount > 0) {
            log.info { "Found $existingCount existing candles for $symbol $timeframe, skipping download" }
            // Still calculate ATR for any candles that don't have it
            candlePersistenceService.calculateAndSaveATR(symbol, timeframe)
            return
        }

        log.info { "Loading historical data for $symbol $timeframe from $startDate" }
        val candles = binanceClient.fetchAllHistoricalData(symbol, timeframe, startDate)

        if (candles.isEmpty()) {
            log.warn { "No candles fetched for $symbol $timeframe" }
            return
        }

        // Save candles in a separate transaction
        candlePersistenceService.saveCandles(symbol, timeframe, candles)

        // Calculate and update ATR in a separate transaction
        candlePersistenceService.calculateAndSaveATR(symbol, timeframe)
    }

    fun getCandlesForBacktest(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
        endDate: Instant,
    ): List<CandleEntity> =
        candleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
            symbol,
            timeframe,
            startDate,
            endDate,
        )

    fun getDataSummary(
        symbol: String,
        timeframe: Timeframe,
    ): String {
        val earliest = candleRepository.findEarliestCandleTime(symbol, timeframe)
        val latest = candleRepository.findLatestCandleTime(symbol, timeframe)
        val count = candleRepository.countBySymbolAndTimeframe(symbol, timeframe)

        return """
            Symbol: $symbol
            Timeframe: ${timeframe.label}
            Candles: $count
            Period: $earliest to $latest
            """.trimIndent()
    }

    suspend fun syncMissingData(
        symbol: String,
        timeframe: Timeframe,
    ): Int {
        val latestCandleTime = candleRepository.findLatestCandleTime(symbol, timeframe)

        val startTime =
            if (latestCandleTime != null) {
                // Start from the next candle after the latest one
                latestCandleTime.plusSeconds(timeframe.minutes * 60L)
            } else {
                // No data exists, use configured start date
                properties.startDate
            }

        val now = Instant.now()
        if (!startTime.isBefore(now)) {
            log.info { "Data for $symbol ${timeframe.label} is already up to date" }
            return 0
        }

        log.info { "Syncing missing data for $symbol ${timeframe.label} from $startTime" }
        val candles = binanceClient.fetchAllHistoricalData(symbol, timeframe, startTime)

        if (candles.isEmpty()) {
            log.info { "No new candles fetched for $symbol ${timeframe.label}" }
            return 0
        }

        val newCandlesAdded = candlePersistenceService.saveCandles(symbol, timeframe, candles)

        // Calculate ATR for new candles
        candlePersistenceService.calculateAndSaveATR(symbol, timeframe)

        log.info { "Synced $newCandlesAdded new candles for $symbol ${timeframe.label}" }
        return newCandlesAdded
    }
}
