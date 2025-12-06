package com.trading.coinflip.data

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DataService(
    private val candleRepository: CandleRepository,
    private val binanceClient: BinanceClient,
    private val properties: BacktestProperties,
    private val databaseClient: DatabaseClient,
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
                log.info {
                    "Got page of ${page.size} candles (total: ${totalSaved + page.size}) symbol=$symbol timeframe=${timeframe.label}"
                }
                val saved = saveCandlePage(page)
                totalSaved += saved
                pageNum++
                log.info { "Page $pageNum: saved $saved candles (total: $totalSaved) symbol=$symbol timeframe=${timeframe.label}" }
            }

        return totalSaved
    }

    /**
     * Save a page of candles to the database.
     * Uses multi-row INSERT for efficiency and to avoid R2DBC ByteBuf memory leaks.
     * Candles are sorted by openTime to ensure correct ATR calculation by database trigger.
     */
    private suspend fun saveCandlePage(candles: List<CandleEntity>): Int {
        if (candles.isEmpty()) return 0
        val sortedCandles = candles.sortedBy { it.openTime }
        val sql = buildCandlesInsert(sortedCandles)
        databaseClient
            .sql(sql)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return sortedCandles.size
    }

    /**
     * Builds a multi-row INSERT statement for candles.
     * Uses ON CONFLICT DO NOTHING to handle already synced candles gracefully.
     * ATR column is omitted - calculated by database trigger on INSERT.
     */
    private fun buildCandlesInsert(candles: List<CandleEntity>): String {
        val values =
            candles.joinToString(", ") { candle ->
                """
                (
                    '${candle.symbol}', '${candle.timeframe.name}',
                    '${candle.openTime}',
                    ${candle.open.toPlainString()}, ${candle.high.toPlainString()},
                    ${candle.low.toPlainString()}, ${candle.close.toPlainString()},
                    ${candle.volume.toPlainString()}
                )
                """.trimIndent().replace("\n", " ")
            }

        return """
            INSERT INTO candles (symbol, timeframe, open_time, open, high, low, close, volume)
            VALUES $values
            ON CONFLICT (symbol, timeframe, open_time) DO NOTHING
            """.trimIndent()
    }
}
