-- Replace individual candle fields with foreign key to candles table
ALTER TABLE live_sessions ADD COLUMN last_candle_id BIGINT REFERENCES candles(id);
ALTER TABLE live_sessions DROP COLUMN last_atr;
ALTER TABLE live_sessions DROP COLUMN last_candle_close;
ALTER TABLE live_sessions DROP COLUMN last_candle_time;
