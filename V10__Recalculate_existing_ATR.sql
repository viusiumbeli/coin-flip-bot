-- V10__Recalculate_existing_ATR.sql
-- One-time migration to recalculate ATR for all existing candles using the new database function.
-- This ensures data consistency after moving ATR calculation from Kotlin to PostgreSQL.

-- Recalculate ATR for all existing candles, processing each symbol/timeframe combination
-- in chronological order to ensure correct EMA chain calculation.
DO $$
DECLARE
    v_symbol VARCHAR(20);
    v_timeframe VARCHAR(20);
    v_candle RECORD;
    v_atr NUMERIC(20, 8);
    v_count INT := 0;
    v_total INT := 0;
BEGIN
    -- Get total candle count for progress reporting
    SELECT COUNT(*) INTO v_total FROM candles;
    RAISE NOTICE 'Recalculating ATR for % total candles', v_total;

    -- Process each distinct symbol/timeframe combination
    FOR v_symbol, v_timeframe IN
        SELECT DISTINCT symbol, timeframe FROM candles ORDER BY symbol, timeframe
    LOOP
        RAISE NOTICE 'Processing % %...', v_symbol, v_timeframe;

        -- Process candles in chronological order for this symbol/timeframe
        FOR v_candle IN
            SELECT id, open_time, high, low, close
            FROM candles
            WHERE symbol = v_symbol AND timeframe = v_timeframe
            ORDER BY open_time ASC
        LOOP
            -- Calculate ATR using the new function
            v_atr := calculate_atr_for_candle(
                v_symbol,
                v_timeframe,
                v_candle.open_time,
                v_candle.high,
                v_candle.low,
                v_candle.close,
                10  -- Default ATR period
            );

            -- Update the candle with calculated ATR
            UPDATE candles SET atr = v_atr WHERE id = v_candle.id;

            v_count := v_count + 1;

            -- Progress logging every 10000 candles
            IF v_count % 10000 = 0 THEN
                RAISE NOTICE 'Progress: % / % candles processed', v_count, v_total;
            END IF;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'ATR recalculation complete: % candles processed', v_count;
END $$;
