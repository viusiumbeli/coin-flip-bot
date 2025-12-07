package com.trading.coinflip.exchange

import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Interface for exchange REST API clients.
 * Handles historical kline/candlestick data fetching.
 */
interface ExchangeClient {
    /**
     * Fetch historical klines from the exchange.
     *
     * @param symbol Trading pair (e.g., "BTCUSDT")
     * @param timeframe Candle timeframe
     * @param startTime Start of range (inclusive)
     * @param endTime End of range (inclusive)
     * @param limit Max candles to return (default 1000)
     * @return List of candles, empty if error occurs
     */
    suspend fun fetchHistoricalKlines(
        symbol: String,
        timeframe: Timeframe,
        startTime: Instant? = null,
        endTime: Instant? = null,
        limit: Int = 1000,
    ): List<CandleEntity>

    /**
     * Stream historical data page by page from startDate to now.
     * Emits pages of candles for efficient batch processing.
     *
     * @param symbol Trading pair
     * @param timeframe Candle timeframe
     * @param startDate Start streaming from this date
     * @return Flow emitting pages of candles
     */
    fun streamHistoricalData(
        symbol: String,
        timeframe: Timeframe,
        startDate: Instant,
    ): Flow<List<CandleEntity>>
}
