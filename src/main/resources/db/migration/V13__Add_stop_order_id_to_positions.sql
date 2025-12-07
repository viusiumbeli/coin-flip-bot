-- Add stop_order_id to track conditional stop orders per position
ALTER TABLE live_positions ADD COLUMN stop_order_id VARCHAR(64);
