-- V9__ATR_database_trigger.sql
-- Move ATR calculation from Kotlin application to PostgreSQL trigger
-- This ensures atomic consistency and eliminates memory issues with large datasets

-- Function to calculate ATR for a single candle
-- ATR = EMA of True Range
-- True Range = max(high - low, |high - prevClose|, |low - prevClose|)
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
    v_candle_count INT;
    v_true_range NUMERIC(20, 8);
    v_multiplier NUMERIC(20, 8);
    v_sma_sum NUMERIC(20, 8);
BEGIN
    -- Get previous candle's close and ATR
    SELECT close, atr
    INTO v_prev_close, v_prev_atr
    FROM candles
    WHERE symbol = p_symbol
      AND timeframe = p_timeframe
      AND open_time < p_open_time
    ORDER BY open_time DESC
    LIMIT 1;

    -- Calculate True Range
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

    -- Count existing candles for this symbol/timeframe (before current)
    SELECT COUNT(*)
    INTO v_candle_count
    FROM candles
    WHERE symbol = p_symbol
      AND timeframe = p_timeframe
      AND open_time < p_open_time;

    -- Not enough data yet for ATR (need at least period candles)
    IF v_candle_count < p_period - 1 THEN
        RETURN NULL;
    END IF;

    -- This is the period-th candle: Calculate initial ATR as SMA of first period TRs
    IF v_candle_count = p_period - 1 THEN
        -- Calculate sum of True Ranges for previous (period-1) candles
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
    END IF;

    -- EMA-based ATR: ATR = prevATR + (1/period) * (currentTR - prevATR)
    IF v_prev_atr IS NULL THEN
        -- Previous candle should have ATR but doesn't - data inconsistency
        -- This can happen during migration, return NULL
        RETURN NULL;
    END IF;

    v_multiplier := ROUND(1.0 / p_period, 8);
    RETURN ROUND(v_prev_atr + v_multiplier * (v_true_range - v_prev_atr), 8);
END;
$$ LANGUAGE plpgsql;

-- Trigger function to set ATR before insert
CREATE OR REPLACE FUNCTION trigger_calculate_atr()
RETURNS TRIGGER AS $$
BEGIN
    -- Only calculate if ATR is not already set
    IF NEW.atr IS NULL THEN
        NEW.atr := calculate_atr_for_candle(
            NEW.symbol,
            NEW.timeframe,
            NEW.open_time,
            NEW.high,
            NEW.low,
            NEW.close,
            10  -- Default ATR period
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create the trigger (BEFORE INSERT, row-level)
-- PostgreSQL processes rows in insert order within a transaction,
-- so row N can reference rows 1..N-1 inserted in the same transaction
DROP TRIGGER IF EXISTS candle_atr_trigger ON candles;
CREATE TRIGGER candle_atr_trigger
    BEFORE INSERT ON candles
    FOR EACH ROW
    EXECUTE FUNCTION trigger_calculate_atr();
