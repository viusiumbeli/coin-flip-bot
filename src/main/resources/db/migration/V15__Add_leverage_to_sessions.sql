-- Add leverage column to live_sessions table
-- Default to 1x (no leverage) for backward compatibility
ALTER TABLE live_sessions ADD COLUMN leverage INT NOT NULL DEFAULT 1;
