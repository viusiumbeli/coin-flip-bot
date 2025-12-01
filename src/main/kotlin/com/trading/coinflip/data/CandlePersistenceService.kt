package com.trading.coinflip.data

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.ATRCalculator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CandlePersistenceService(
    private val candleRepository: CandleRepository,
    private val atrCalculator: ATRCalculator,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    suspend fun saveCandles(
        symbol: String,
        timeframe: Timeframe,
        candles: List<CandleEntity>,
    ): Int {
        // Fetch only openTime values (projection query - much less memory than full entities)
        val existingOpenTimes =
            candleRepository
                .findOpenTimesBySymbolAndTimeframe(symbol, timeframe)
                .map { it.openTime }
                .toSet()

        // Filter out candles that already exist (in memory)
        val newCandles = candles.filter { it.openTime !in existingOpenTimes }

        // Batch insert all new candles at once
        if (newCandles.isNotEmpty()) {
            candleRepository.saveAll(newCandles).toList()
            log.info { "Saved ${newCandles.size} new candles for $symbol $timeframe" }
            return newCandles.size
        }
        log.info { "No new candles to save for $symbol $timeframe" }
        return 0
    }

    @Transactional
    suspend fun calculateAndSaveATR(
        symbol: String,
        timeframe: Timeframe,
        period: Int = 10,
    ) {
        log.info { "Calculating ATR for $symbol $timeframe with period $period" }

        val candlesWithoutATR = candleRepository.findCandlesWithoutATR(symbol, timeframe).toList()

        if (candlesWithoutATR.isEmpty()) {
            log.info { "All candles already have ATR calculated, skipping" }
            return
        }

        log.info { "Found ${candlesWithoutATR.size} candles without ATR" }

        // Second DB call: check if incremental mode possible
        val lastCandleWithATR = candleRepository.findLastCandleWithATR(symbol, timeframe)

        if (lastCandleWithATR != null) {
            // Filter to only include candles AFTER the last candle with ATR
            // Early candles (first period-1) should have NULL ATR by design
            val newCandlesAfterLast =
                candlesWithoutATR.filter {
                    it.openTime > lastCandleWithATR.openTime
                }

            if (newCandlesAfterLast.isEmpty()) {
                log.info { "No new candles after last ATR (${candlesWithoutATR.size} early candles have NULL ATR by design)" }
                return
            }

            // Incremental mode: only calculate ATR for new candles
            log.info { "Using incremental ATR calculation from last known ATR" }

            val calcStartTime = System.currentTimeMillis()
            atrCalculator.calculateATRIncremental(lastCandleWithATR, newCandlesAfterLast, period)
            val calcTime = System.currentTimeMillis() - calcStartTime
            log.info { "Incremental ATR calculation completed in ${calcTime}ms" }

            saveATRUpdatesInBatches(newCandlesAfterLast)
            return
        }

        // Full calculation mode: no existing ATR, need to calculate from scratch
        log.info { "No existing ATR found, performing full calculation..." }
        val allCandles = candleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe).toList()

        if (allCandles.size < period) {
            log.warn { "Not enough candles to calculate ATR. Need at least $period, got ${allCandles.size}" }
            return
        }

        // Calculate ATR for all candles
        val calcStartTime = System.currentTimeMillis()
        val candlesWithATR = atrCalculator.calculateATR(allCandles, period)
        val calcTime = System.currentTimeMillis() - calcStartTime
        log.info { "ATR calculation completed in ${calcTime}ms" }

        // Filter only candles that now have ATR
        val candlesNeedingUpdate = candlesWithATR.filter { it.atr != null }

        if (candlesNeedingUpdate.isEmpty()) {
            log.info { "No candles need ATR updates" }
            return
        }

        // Save updated candles in batches
        saveATRUpdatesInBatches(candlesNeedingUpdate)
    }

    private suspend fun saveATRUpdatesInBatches(candles: List<CandleEntity>) {
        log.info { "Updating ${candles.size} candles with ATR values in batches..." }

        val batchSize = 1000
        val batches = candles.chunked(batchSize)
        val updateStartTime = System.currentTimeMillis()

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            candleRepository.saveAll(batch).toList()
            val batchTime = System.currentTimeMillis() - batchStartTime

            val processed = ((index + 1) * batchSize).coerceAtMost(candles.size)
            val progress = (processed * 100.0 / candles.size).toInt()
            log.info {
                "Batch ${index + 1}/${batches.size}: Updated ${batch.size} candles in ${batchTime}ms (Progress: $progress%, Total: $processed/${candles.size})"
            }
        }

        val totalUpdateTime = System.currentTimeMillis() - updateStartTime
        log.info { "Successfully updated ${candles.size} candles with ATR in ${totalUpdateTime}ms" }
    }
}
