-- Add trailing stop configuration per session
ALTER TABLE live_sessions ADD COLUMN trailing_stop_mode VARCHAR(20) DEFAULT 'ATR';
ALTER TABLE live_sessions ADD COLUMN trailing_stop_percent DECIMAL(10,4) DEFAULT 1.0;
ALTER TABLE live_sessions ADD COLUMN atr_multiplier DECIMAL(10,4) DEFAULT 3.0;

COMMENT ON COLUMN live_sessions.trailing_stop_mode IS 'ATR or PERCENT - determines how trailing stop distance is calculated';
COMMENT ON COLUMN live_sessions.trailing_stop_percent IS 'Used when mode=PERCENT: distance = price * percent / 100';
COMMENT ON COLUMN live_sessions.atr_multiplier IS 'Used when mode=ATR: distance = ATR * multiplier';
