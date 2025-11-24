-- Reset ATR values for BTCUSDT FOUR_HOURS
UPDATE candles
SET atr = NULL
WHERE symbol = 'BTCUSDT'
  AND timeframe = 'FOUR_HOURS';
