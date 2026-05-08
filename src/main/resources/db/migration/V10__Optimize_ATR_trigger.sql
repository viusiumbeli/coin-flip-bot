-- V10__Optimize_ATR_trigger.sql
-- Optimize ATR calculation trigger by eliminating COUNT(*) query
-- Performance: O(1) for most cases instead of O(n)
--
-- Problem: The previous implementation used COUNT(*) to determine candle position,
-- which scans ALL previous candles - O(n) complexity that slows down as table grows.
--
-- Solution: Use v_prev_atr as a state indicator:
-- - If v_prev_atr IS NOT NULL -> Use EMA formula directly (O(1))
-- - If v_prev_atr IS NULL -> Use EXISTS with OFFSET to check warmup (O(period))

CREATE OR REPLACE FUNCTION calculate_atr_for_candle(
    p_symbol VARCHAR(20),
    p_timeframe VARCHAR(20),
    p_open_time TIMESTAMP,
    p_high NUMERIC(20, 8),
    p_low NUMERIC(20, 8),
    p_close NUMERIC(20, 8),
    p_period INT DEFAULT 10
) RETURNS NUMERIC(20, 8) AS $$
DECLARE
    v_prev_close NUMERIC(20, 8);
    v_prev_atr NUMERIC(20, 8);
    v_true_range NUMERIC(20, 8);
    v_multiplier NUMERIC(20, 8);
    v_sma_sum NUMERIC(20, 8);
    v_has_enough_candles BOOLEAN;
BEGIN
    -- Step 1: Get previous candle's close and ATR
    SELECT close, atr
    INTO v_prev_close, v_prev_atr
    FROM candles
    WHERE symbol = p_symbol
      AND timeframe = p_timeframe
      AND open_time < p_open_time
    ORDER BY open_time DESC
    LIMIT 1;

    -- Step 2: Calculate True Range
    IF v_prev_close IS NULL THEN
        -- First candle: TR = High - Low
        v_true_range := p_high - p_low;
    ELSE
        -- TR = max(high - low, |high - prevClose|, |low - prevClose|)
        v_true_range := GREATEST(
            p_high - p_low,
            ABS(p_high - v_prev_close),
            ABS(p_low - v_prev_close)
        );
    END IF;

    -- Step 3: FAST PATH - If previous candle has ATR, use EMA formula
    -- This is O(1) and handles 99%+ of cases after warmup period
    IF v_prev_atr IS NOT NULL THEN
        v_multiplier := ROUND(1.0 / p_period, 8);
        RETURN ROUND(v_prev_atr + v_multiplier * (v_true_range - v_prev_atr), 8);
    END IF;

    -- Step 4: Previous ATR is NULL - either in warmup phase or at initial SMA candle
    -- Use EXISTS with OFFSET to check if we have at least (period-1) previous candles
    -- To check if at least 9 rows exist: skip 8, check if 9th exists
    -- This is O(period) because it stops after finding the (period-1)th row
    SELECT EXISTS (
        SELECT 1
        FROM candles
        WHERE symbol = p_symbol
          AND timeframe = p_timeframe
          AND open_time < p_open_time
        ORDER BY open_time
        OFFSET p_period - 2
        LIMIT 1
    ) INTO v_has_enough_candles;

    -- Not enough candles yet (less than period-1 previous candles)
    IF NOT v_has_enough_candles THEN
        RETURN NULL;
    END IF;

    -- Step 5: This is exactly the period-th candle - calculate initial ATR as SMA
    -- This CTE only runs once per symbol/timeframe combination
    WITH ordered_candles AS (
        SELECT
            c.high,
            c.low,
            c.close,
            LAG(c.close) OVER (ORDER BY c.open_time) AS prev_close
        FROM candles c
        WHERE c.symbol = p_symbol
          AND c.timeframe = p_timeframe
          AND c.open_time < p_open_time
        ORDER BY c.open_time
    ),
    true_ranges AS (
        SELECT
            CASE
                WHEN prev_close IS NULL THEN high - low
                ELSE GREATEST(
                    high - low,
                    ABS(high - prev_close),
                    ABS(low - prev_close)
                )
            END AS tr
        FROM ordered_candles
    )
    SELECT COALESCE(SUM(tr), 0) INTO v_sma_sum FROM true_ranges;

    -- Add current TR and divide by period
    RETURN ROUND((v_sma_sum + v_true_range) / p_period, 8);
END;
$$ LANGUAGE plpgsql;

-- Note: No need to recreate the trigger - PostgreSQL uses the updated function automatically
