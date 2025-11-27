-- Add variance/distribution metrics for totalReturnPercent
ALTER TABLE experiments ADD COLUMN return_std_dev NUMERIC(20, 8);
ALTER TABLE experiments ADD COLUMN return_min NUMERIC(20, 8);
ALTER TABLE experiments ADD COLUMN return_max NUMERIC(20, 8);
ALTER TABLE experiments ADD COLUMN return_p5 NUMERIC(20, 8);
ALTER TABLE experiments ADD COLUMN return_p25 NUMERIC(20, 8);
ALTER TABLE experiments ADD COLUMN return_p50 NUMERIC(20, 8);
ALTER TABLE experiments ADD COLUMN return_p75 NUMERIC(20, 8);
ALTER TABLE experiments ADD COLUMN return_p95 NUMERIC(20, 8);
