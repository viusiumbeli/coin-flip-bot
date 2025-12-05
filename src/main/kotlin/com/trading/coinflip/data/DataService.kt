package com.trading.coinflip.data

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import kotlinx.coroutines.flow.toList
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

    /**
     * Syncs missing data from the latest candle to now.
     * Downloads and saves page-by-page for crash safety.
     */
    suspend fun syncMissingData(
        symbol: String,
        timeframe: Timeframe,
    ): Int {
        val latestCandleTime = candleRepository.findLatestCandle(symbol, timeframe)?.openTime

        val startTime =
            if (latestCandleTime != null) {
                latestCandleTime.plusSeconds(timeframe.minutes * 60L)
            } else {
                properties.startDate
            }

        val now = Instant.now()
        if (!startTime.isBefore(now)) {
            log.info { "Data for $symbol ${timeframe.label} is already up to date" }
            return 0
        }

        log.info { "Syncing missing data for $symbol ${timeframe.label} from $startTime" }
        val totalSaved = streamAndSave(symbol, timeframe, startTime)

        if (totalSaved == 0) {
            log.info { "No new candles for $symbol ${timeframe.label}" }
            return 0
        }

        candlePersistenceService.calculateAndSaveATR(symbol, timeframe)
        log.info { "Synced $totalSaved new candles for $symbol ${timeframe.label}" }
        return totalSaved
    }

    private suspend fun streamAndSave(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
    ): Int {
        var totalSaved = 0
        var pageNum = 0

        binanceClient
            .streamHistoricalData(symbol, timeframe, startDate)
            .collect { page ->
                val saved = candlePersistenceService.saveCandlePage(page)
                totalSaved += saved
                pageNum++
                log.info { "Page $pageNum: saved $saved candles (total: $totalSaved)" }
            }

        return totalSaved
    }

    /**
     * Fetches candles from database only. No network calls.
     */
    suspend fun getCandlesForBacktest(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
        endDate: Instant,
    ): List<CandleEntity> =
        candleRepository
            .findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                symbol,
                timeframe,
                startDate,
                endDate,
            ).toList()
}
