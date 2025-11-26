-- Add async experiment execution support

-- Add status tracking columns to experiments table
ALTER TABLE experiments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE experiments ADD COLUMN completed_runs INT NOT NULL DEFAULT 0;
ALTER TABLE experiments ADD COLUMN failed_runs INT NOT NULL DEFAULT 0;
ALTER TABLE experiments ADD COLUMN started_at TIMESTAMP;
ALTER TABLE experiments ADD COLUMN finished_at TIMESTAMP;
ALTER TABLE experiments ADD COLUMN error_message TEXT;

-- Create index for status queries
CREATE INDEX idx_experiments_status ON experiments(status);

-- Update existing experiments to have default completed_runs = num_backtests
UPDATE experiments SET completed_runs = num_backtests WHERE status = 'COMPLETED';
