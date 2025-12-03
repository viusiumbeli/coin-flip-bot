ALTER TABLE live_positions
    DROP COLUMN exit_time,
    DROP COLUMN exit_price,
    DROP COLUMN exit_reason,
    DROP COLUMN profit_loss,
    DROP COLUMN profit_loss_percent,
    DROP COLUMN balance_before_close,
    DROP COLUMN balance_after_close;
