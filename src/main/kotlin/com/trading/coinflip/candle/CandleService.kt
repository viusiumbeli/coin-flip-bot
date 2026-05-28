package com.trading.coinflip.candle

import com.trading.coinflip.common.config.BacktestProperties
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.exchange.Exchange
import com.trading.coinflip.exchange.ExchangeClient
import com.trading.coinflip.exchange.ExchangeClientFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CandleService(
    private val candleRepository: CandleRepository,
    private val exchangeClientFactory: ExchangeClientFactory,
    private val properties: BacktestProperties,
    private val databaseClient: DatabaseClient,
) {
    private val log = KotlinLogging.logger {}

    // Lazily create REST client (uses configured exchange)
    private val exchangeClient by lazy { exchangeClientFactory.getRestClient() }

    /**
     * Syncs missing data from the latest candle to now.
     * Downloads and saves page-by-page for crash safety.
     * @param exchange Optional exchange override; uses default if null.
     */
    suspend fun syncMissingData(
        symbol: String,
        timeframe: Timeframe,
        exchange: Exchange? = null,
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

        val resolvedExchange = exchange ?: exchangeClientFactory.getExchange()
        val client = exchangeClientFactory.getRestClient(resolvedExchange)
        log.info { "Syncing missing data for $symbol ${timeframe.label} from $startTime via $resolvedExchange" }
        val totalSaved = streamAndSave(client, symbol, timeframe, startTime)

        if (totalSaved == 0) {
            log.info { "No new candles for $symbol ${timeframe.label}" }
            return 0
        }

        log.info { "Synced $totalSaved new candles for $symbol ${timeframe.label}" }
        return totalSaved
    }

    /**
     * Load candles in parallel pages for fast bulk reads.
     * Returns all candles in chronological order.
     */
    suspend fun loadCandlesParallel(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant,
        endTime: Instant,
    ): List<CandleEntity> {
        val pageSize = properties.candlePageSize

        val totalCandles =
            candleRepository.countCandlesInRange(
                symbol = symbol,
                timeframe = timeframe,
                startTime = startTime,
                endTime = endTime,
            )

        if (totalCandles == 0L) {
            return emptyList()
        }

        val pageCount = ((totalCandles + pageSize - 1) / pageSize).toInt()
        log.info { "Loading $totalCandles candles in $pageCount parallel pages" }

        val pages =
            coroutineScope {
                (0 until pageCount)
                    .map { pageIndex ->
                        async {
                            val offset = pageIndex.toLong() * pageSize
                            candleRepository
                                .findCandlesPageByOffset(
                                    symbol = symbol,
                                    timeframe = timeframe,
                                    startTime = startTime,
                                    endTime = endTime,
                                    limit = pageSize,
                                    offset = offset,
                                ).toList()
                        }
                    }.awaitAll()
            }

        log.info { "All $pageCount pages loaded" }
        return pages.flatten()
    }

    private suspend fun streamAndSave(
        client: ExchangeClient,
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
    ): Int {
        var totalSaved = 0
        var pageNum = 0

        client
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

    /**
     * Fetches aggregated stats for all symbol/timeframe combinations in a single query.
     * Returns count, earliest, and latest candle for each combination.
     */
    suspend fun getAllCandleStats(): List<CandleStats> =
        databaseClient
            .sql(
                """
                SELECT symbol, timeframe,
                       COUNT(*) as candle_count,
                       MIN(open_time) as earliest,
                       MAX(open_time) as latest
                FROM candles
                GROUP BY symbol, timeframe
                """,
            ).map { row, _ ->
                CandleStats(
                    symbol = row.get("symbol", String::class.java)!!,
                    timeframe = row.get("timeframe", String::class.java)!!,
                    candleCount = row.get("candle_count", Long::class.javaObjectType)!!,
                    earliest = row.get("earliest", Instant::class.java),
                    latest = row.get("latest", Instant::class.java),
                )
            }.all()
            .asFlow()
            .toList()
}
