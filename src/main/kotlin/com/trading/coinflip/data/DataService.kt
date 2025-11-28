package com.trading.coinflip.data

import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.Timeframe
import com.trading.coinflip.strategy.ATRCalculator
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DataService(
    private val candleRepository: CandleRepository,
    private val binanceClient: BinanceClient,
    private val atrCalculator: ATRCalculator,
) {
    private val log = KotlinLogging.logger {}

    fun loadHistoricalData(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
        forceReload: Boolean = false,
    ) = runBlocking {
        if (!forceReload) {
            val existingCount =
                candleRepository
                    .findBySymbolAndTimeframeFromDate(
                        symbol,
                        timeframe,
                        startDate,
                    ).size

            if (existingCount > 0) {
                log.info { "Found $existingCount existing candles for $symbol $timeframe, skipping download" }
                // Still calculate ATR for any candles that don't have it
                calculateAndSaveATR(symbol, timeframe)
                return@runBlocking
            }
        }

        log.info { "Loading historical data for $symbol $timeframe from $startDate" }
        val candles = binanceClient.fetchAllHistoricalData(symbol, timeframe, startDate)

        if (candles.isEmpty()) {
            log.warn { "No candles fetched for $symbol $timeframe" }
            return@runBlocking
        }

        // Save candles in a separate transaction
        saveCandles(symbol, timeframe, candles)

        // Calculate and update ATR in a separate transaction
        calculateAndSaveATR(symbol, timeframe)
    }

    @Transactional
    fun saveCandles(
        symbol: String,
        timeframe: Timeframe,
        candles: List<Candle>,
    ) {
        // Fetch all existing open times in a single query
        val existingOpenTimes =
            candleRepository
                .findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe)
                .map { it.openTime }
                .toSet()

        // Filter out candles that already exist (in memory)
        val newCandles = candles.filter { it.openTime !in existingOpenTimes }

        // Batch insert all new candles at once
        if (newCandles.isNotEmpty()) {
            val savedCandles = candleRepository.saveAll(newCandles).toList()
            log.info { "Saved ${savedCandles.size} new candles for $symbol $timeframe" }
        } else {
            log.info { "No new candles to save for $symbol $timeframe" }
        }
    }

    @Transactional
    fun calculateAndSaveATR(
        symbol: String,
        timeframe: Timeframe,
        period: Int = 10,
    ) {
        log.info { "Calculating ATR for $symbol $timeframe with period $period" }

        // Check how many candles need ATR calculation
        val totalCandles = candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe).size
        val candlesWithoutATR = candleRepository.countCandlesWithoutATR(symbol, timeframe)

        if (candlesWithoutATR == 0L) {
            log.info { "All $totalCandles candles already have ATR calculated, skipping" }
            return
        }

        log.info { "Found $candlesWithoutATR candles without ATR out of $totalCandles total" }

        // Load all candles to calculate ATR correctly (need previous candles for context)
        val startTime = System.currentTimeMillis()
        log.info { "Loading all candles for ATR calculation..." }

        val allCandles = candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe)
        val loadTime = System.currentTimeMillis() - startTime
        log.info { "Loaded ${allCandles.size} candles in ${loadTime}ms" }

        if (allCandles.size < period) {
            log.warn { "Not enough candles to calculate ATR. Need at least $period, got ${allCandles.size}" }
            return
        }

        // Track which candles originally had no ATR
        val candleIdsWithoutATR = allCandles.filter { it.atr == null }.mapNotNull { it.id }.toSet()

        // Calculate ATR for all candles (needed for continuity)
        log.info { "Calculating ATR values..." }
        val calcStartTime = System.currentTimeMillis()
        val candlesWithATR = atrCalculator.calculateATR(allCandles, period)
        val calcTime = System.currentTimeMillis() - calcStartTime
        log.info { "ATR calculation completed in ${calcTime}ms" }

        // Filter only candles that need to be updated (those that originally had no ATR)
        val candlesNeedingUpdate = candlesWithATR.filter { it.id != null && it.id in candleIdsWithoutATR && it.atr != null }

        if (candlesNeedingUpdate.isEmpty()) {
            log.info { "No candles need ATR updates" }
            return
        }

        log.info { "Updating ${candlesNeedingUpdate.size} candles with ATR values in batches..." }

        // Process in batches for better performance
        val batchSize = 1000
        val batches = candlesNeedingUpdate.chunked(batchSize)
        val updateStartTime = System.currentTimeMillis()

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            candleRepository.saveAll(batch)
            val batchTime = System.currentTimeMillis() - batchStartTime

            val processed = ((index + 1) * batchSize).coerceAtMost(candlesNeedingUpdate.size)
            val progress = (processed * 100.0 / candlesNeedingUpdate.size).toInt()
            log.info {
                "Batch ${index + 1}/${batches.size}: Updated ${batch.size} candles in ${batchTime}ms (Progress: $progress%, Total: $processed/${candlesNeedingUpdate.size})"
            }
        }

        val totalUpdateTime = System.currentTimeMillis() - updateStartTime
        log.info { "Successfully updated ${candlesNeedingUpdate.size} candles with ATR in ${totalUpdateTime}ms" }
    }

    fun getCandlesForBacktest(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant? = null,
        endDate: Instant? = null,
    ): List<Candle> =
        if (startDate != null && endDate != null) {
            candleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                symbol,
                timeframe,
                startDate,
                endDate,
            )
        } else if (startDate != null) {
            candleRepository.findBySymbolAndTimeframeFromDate(symbol, timeframe, startDate)
        } else {
            candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe)
        }

    fun getDataSummary(
        symbol: String,
        timeframe: Timeframe,
    ): String {
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

    fun syncMissingData(
        symbol: String,
        timeframe: Timeframe,
    ): Int =
        runBlocking {
            val latestCandleTime = candleRepository.findLatestCandleTime(symbol, timeframe)

            val startTime =
                if (latestCandleTime != null) {
                    // Start from the next candle after the latest one
                    latestCandleTime.plusSeconds(timeframe.minutes * 60L)
                } else {
                    // No data exists, use configured start date
                    Instant.parse("2020-01-01T00:00:00Z")
                }

            val now = Instant.now()
            if (!startTime.isBefore(now)) {
                log.info { "Data for $symbol ${timeframe.label} is already up to date" }
                return@runBlocking 0
            }

            log.info { "Syncing missing data for $symbol ${timeframe.label} from $startTime" }
            val candles = binanceClient.fetchAllHistoricalData(symbol, timeframe, startTime)

            if (candles.isEmpty()) {
                log.info { "No new candles fetched for $symbol ${timeframe.label}" }
                return@runBlocking 0
            }

            val countBefore = candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe).size
            saveCandles(symbol, timeframe, candles)
            val countAfter = candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe).size
            val newCandlesAdded = countAfter - countBefore

            // Calculate ATR for new candles
            calculateAndSaveATR(symbol, timeframe)

            log.info { "Synced $newCandlesAdded new candles for $symbol ${timeframe.label}" }
            return@runBlocking newCandlesAdded
        }
}
