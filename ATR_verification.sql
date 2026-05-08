WITH consecutive AS (
    SELECT
        open_time,
        high, low, close,
    atr,
    LAG(close) OVER w AS prev_close,
    LAG(atr) OVER w AS prev_atr
FROM candles
WHERE symbol = 'BNBUSDT' AND timeframe = 'ONE_MINUTE'
    WINDOW w AS (ORDER BY open_time)
    )
SELECT
    open_time,
    atr AS stored_atr,
    prev_atr,
    GREATEST(high - low, ABS(high - prev_close), ABS(low - prev_close)) AS tr,
    ROUND(prev_atr + 0.1 * (
        GREATEST(high - low, ABS(high - prev_close), ABS(low - prev_close)) - prev_atr
        ), 8) AS expected_atr
FROM consecutive
WHERE prev_atr IS NOT NULL
  AND atr != ROUND(prev_atr + 0.1 * (
    GREATEST(high - low, ABS(high - prev_close), ABS(low - prev_close)) - prev_atr
    ), 8)
ORDER BY open_time;