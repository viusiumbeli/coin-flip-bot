-- Widen symbol column from VARCHAR(20) to VARCHAR(50)
-- Deribit option instruments have long names (e.g. BTC-28MAR26-1000000-C = 21 chars)

ALTER TABLE candles ALTER COLUMN symbol TYPE VARCHAR(50);
ALTER TABLE live_sessions ALTER COLUMN symbol TYPE VARCHAR(50);
ALTER TABLE live_positions ALTER COLUMN symbol TYPE VARCHAR(50);
ALTER TABLE live_trades ALTER COLUMN symbol TYPE VARCHAR(50);

-- Recreate ATR function with widened parameter type
CREATE OR REPLACE FUNCTION calculate_atr_for_candle(
    p_symbol VARCHAR(50),
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
        v_true_range := p_high - p_low;
    ELSE
        v_true_range := GREATEST(
            p_high - p_low,
            ABS(p_high - v_prev_close),
            ABS(p_low - v_prev_close)
        );
    END IF;

    -- Step 3: FAST PATH - If previous candle has ATR, use EMA formula
    IF v_prev_atr IS NOT NULL THEN
        v_multiplier := ROUND(1.0 / p_period, 8);
        RETURN ROUND(v_prev_atr + v_multiplier * (v_true_range - v_prev_atr), 8);
    END IF;

    -- Step 4: Previous ATR is NULL - either in warmup phase or at initial SMA candle
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

    IF NOT v_has_enough_candles THEN
        RETURN NULL;
    END IF;

    -- Step 5: This is exactly the period-th candle - calculate initial ATR as SMA
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

    RETURN ROUND((v_sma_sum + v_true_range) / p_period, 8);
END;
$$ LANGUAGE plpgsql;
