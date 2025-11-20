-- Initial schema for coin flip bot

CREATE TABLE candles (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    timeframe VARCHAR(20) NOT NULL,
    open_time TIMESTAMP NOT NULL,
    open NUMERIC(20, 8) NOT NULL,
    high NUMERIC(20, 8) NOT NULL,
    low NUMERIC(20, 8) NOT NULL,
    close NUMERIC(20, 8) NOT NULL,
    volume NUMERIC(20, 8) NOT NULL,
    atr NUMERIC(20, 8)
);

-- Create unique index for efficient lookups and prevent duplicates
CREATE UNIQUE INDEX idx_candles_symbol_timeframe_time
ON candles(symbol, timeframe, open_time);
