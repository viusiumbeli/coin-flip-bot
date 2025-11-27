-- Add column to track how many runs beat buy & hold benchmark
ALTER TABLE experiments ADD COLUMN runs_beat_buy_hold INTEGER NOT NULL DEFAULT 0;
