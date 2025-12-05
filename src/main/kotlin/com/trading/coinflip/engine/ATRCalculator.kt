package com.trading.coinflip.engine

import com.trading.coinflip.data.CandleEntity
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ATR Calculator - DEPRECATED
 *
 * ATR is now calculated atomically by PostgreSQL BEFORE INSERT trigger on the candles table.
 * See: V9__ATR_database_trigger.sql
 *
 * This class is kept for reference and testing purposes only.
 */
@Deprecated(
    message = "ATR is now calculated by PostgreSQL database trigger. See V9__ATR_database_trigger.sql",
    level = DeprecationLevel.WARNING,
)
@Component
class ATRCalculator {
    /**
     * Calculate Average True Range (ATR) for a list of candles
     * ATR = EMA of True Range
     * True Range = max(high - low, abs(high - prevClose), abs(low - prevClose))
     */
    fun calculateATR(
        candles: List<CandleEntity>,
        period: Int = 10,
    ): List<CandleEntity> {
        if (candles.size < period) {
            return candles
        }

        val result = candles.toMutableList()

        // Calculate True Range for each candle
        val trueRanges =
            candles.mapIndexed { i, candle ->
                if (i == 0) {
                    // First candle: TR = High - Low
                    candle.high - candle.low
                } else {
                    val prevClose = candles[i - 1].close
                    val highLow = candle.high - candle.low
                    val highPrevClose = (candle.high - prevClose).abs()
                    val lowPrevClose = (candle.low - prevClose).abs()
                    maxOf(highLow, highPrevClose, lowPrevClose)
                }
            }

        // Calculate initial ATR (SMA of first 'period' TRs)
        var atr =
            trueRanges
                .take(period)
                .reduce { acc, tr -> acc + tr }
                .divide(BigDecimal(period), 8, RoundingMode.HALF_UP)

        // Set ATR for the first 'period' candle
        result[period - 1].atr = atr

        // Calculate EMA-based ATR for remaining candles
        val multiplier = BigDecimal.ONE.divide(BigDecimal(period), 8, RoundingMode.HALF_UP)

        for (i in period until candles.size) {
            // ATR = ((period - 1) * prevATR + currentTR) / period
            // Or using EMA formula: ATR = prevATR + multiplier * (currentTR - prevATR)
            atr =
                (atr + multiplier * (trueRanges[i] - atr))
                    .setScale(8, RoundingMode.HALF_UP)
            result[i].atr = atr
        }

        return result
    }

    /**
     * Calculate ATR incrementally for new candles, continuing from a known ATR value.
     * This is memory-efficient for sync operations where only a few new candles need ATR.
     *
     * @param previousCandle The last candle that has ATR calculated (used for prevClose in TR calculation)
     * @param newCandles Candles without ATR, ordered by openTime ASC
     * @param period ATR period (default 10)
     * @return List of newCandles with ATR values set
     */
    fun calculateATRIncremental(
        previousCandle: CandleEntity,
        newCandles: List<CandleEntity>,
        period: Int = 10,
    ): List<CandleEntity> {
        if (newCandles.isEmpty()) {
            return newCandles
        }

        val previousATR = previousCandle.atr ?: return newCandles
        var prevClose = previousCandle.close
        var atr = previousATR
        val multiplier = BigDecimal.ONE.divide(BigDecimal(period), 8, RoundingMode.HALF_UP)

        for (candle in newCandles) {
            // Calculate True Range
            val highLow = candle.high - candle.low
            val highPrevClose = (candle.high - prevClose).abs()
            val lowPrevClose = (candle.low - prevClose).abs()
            val trueRange = maxOf(highLow, highPrevClose, lowPrevClose)

            // EMA formula: ATR = prevATR + multiplier * (currentTR - prevATR)
            atr =
                (atr + multiplier * (trueRange - atr))
                    .setScale(8, RoundingMode.HALF_UP)
            candle.atr = atr
            prevClose = candle.close
        }

        return newCandles
    }
}
