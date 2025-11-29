package com.trading.coinflip.engine

import com.trading.coinflip.common.model.CandleEntity
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

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
        val trueRanges = mutableListOf<BigDecimal>()

        // Calculate True Range for each candle
        for (i in candles.indices) {
            val candle = candles[i]
            val tr =
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
            trueRanges.add(tr)
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
     * Get ATR value for a specific candle, looking back if current candle doesn't have ATR
     */
    fun getATRForCandle(
        candles: List<CandleEntity>,
        index: Int,
    ): BigDecimal? {
        if (index < 0 || index >= candles.size) {
            return null
        }

        // Look backwards for the nearest ATR value
        for (i in index downTo 0) {
            candles[i].atr?.let { return it }
        }

        return null
    }
}
