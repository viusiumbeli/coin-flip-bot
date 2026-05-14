-- Add exchange column to live_sessions
-- Allows tracking which exchange each session uses

ALTER TABLE live_sessions
ADD COLUMN exchange VARCHAR(20) NOT NULL DEFAULT 'BINANCE';

-- Create index for filtering by exchange
CREATE INDEX idx_live_sessions_exchange ON live_sessions(exchange);

COMMENT ON COLUMN live_sessions.exchange IS 'Exchange used for this session: BINANCE or BYBIT';
