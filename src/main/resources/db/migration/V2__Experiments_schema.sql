-- Experiments schema for storing backtest experiments and their trades

CREATE TABLE experiments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    custom_name VARCHAR(255),
    notes TEXT,
    symbol VARCHAR(20) NOT NULL,
    timeframe VARCHAR(20) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    num_backtests INT NOT NULL DEFAULT 1,

    -- Backtest configuration
    initial_capital NUMERIC(20, 8) NOT NULL,
    risk_per_trade NUMERIC(10, 4) NOT NULL,
    atr_period INT NOT NULL,
    atr_multiplier NUMERIC(10, 4) NOT NULL,
    transaction_cost_percent NUMERIC(10, 4) NOT NULL,
    max_concurrent_positions INT NOT NULL,

    -- Aggregated results (averages across all runs)
    final_capital NUMERIC(20, 8) NOT NULL,
    total_return NUMERIC(20, 8) NOT NULL,
    total_return_percent NUMERIC(20, 8) NOT NULL,
    max_drawdown NUMERIC(20, 8) NOT NULL,
    max_drawdown_percent NUMERIC(20, 8) NOT NULL,
    win_rate NUMERIC(10, 4) NOT NULL,
    profit_factor NUMERIC(20, 8) NOT NULL,
    sharpe_ratio NUMERIC(20, 8) NOT NULL,
    total_trades INT NOT NULL,
    winning_trades INT NOT NULL,
    losing_trades INT NOT NULL,
    average_win NUMERIC(20, 8) NOT NULL,
    average_loss NUMERIC(20, 8) NOT NULL,
    largest_win NUMERIC(20, 8) NOT NULL,
    largest_loss NUMERIC(20, 8) NOT NULL,
    average_trade_duration BIGINT NOT NULL,
    buy_and_hold_return NUMERIC(20, 8) NOT NULL,
    buy_and_hold_return_percent NUMERIC(20, 8) NOT NULL
);

CREATE INDEX idx_experiments_symbol_timeframe ON experiments(symbol, timeframe);
CREATE INDEX idx_experiments_created_at ON experiments(created_at DESC);

-- Backtest runs table for individual run results
CREATE TABLE backtest_runs (
    id BIGSERIAL PRIMARY KEY,
    experiment_id BIGINT NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    run_number INT NOT NULL,

    -- Results
    final_capital NUMERIC(20, 8) NOT NULL,
    total_return NUMERIC(20, 8) NOT NULL,
    total_return_percent NUMERIC(20, 8) NOT NULL,
    max_drawdown NUMERIC(20, 8) NOT NULL,
    max_drawdown_percent NUMERIC(20, 8) NOT NULL,
    win_rate NUMERIC(10, 4) NOT NULL,
    profit_factor NUMERIC(20, 8) NOT NULL,
    sharpe_ratio NUMERIC(20, 8) NOT NULL,
    total_trades INT NOT NULL,
    winning_trades INT NOT NULL,
    losing_trades INT NOT NULL,
    average_win NUMERIC(20, 8) NOT NULL,
    average_loss NUMERIC(20, 8) NOT NULL,
    largest_win NUMERIC(20, 8) NOT NULL,
    largest_loss NUMERIC(20, 8) NOT NULL,
    average_trade_duration BIGINT NOT NULL,
    buy_and_hold_return NUMERIC(20, 8) NOT NULL,
    buy_and_hold_return_percent NUMERIC(20, 8) NOT NULL
);

CREATE INDEX idx_backtest_runs_experiment_id ON backtest_runs(experiment_id);

-- Experiment trades table to store individual trades
CREATE TABLE experiment_trades (
    id BIGSERIAL PRIMARY KEY,
    backtest_run_id BIGINT NOT NULL REFERENCES backtest_runs(id) ON DELETE CASCADE,
    trade_number INT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    timeframe VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    entry_time TIMESTAMP NOT NULL,
    entry_price NUMERIC(20, 8) NOT NULL,
    exit_time TIMESTAMP NOT NULL,
    exit_price NUMERIC(20, 8) NOT NULL,
    position_size NUMERIC(20, 8) NOT NULL,
    initial_stop_loss NUMERIC(20, 8) NOT NULL,
    trailing_stop NUMERIC(20, 8) NOT NULL,
    profit_loss NUMERIC(20, 8) NOT NULL,
    profit_loss_percent NUMERIC(20, 8) NOT NULL,
    exit_reason VARCHAR(100) NOT NULL,
    balance_before_open NUMERIC(20, 8) NOT NULL,
    balance_after_open NUMERIC(20, 8) NOT NULL,
    balance_before_close NUMERIC(20, 8) NOT NULL,
    balance_after_close NUMERIC(20, 8) NOT NULL
);

CREATE INDEX idx_experiment_trades_backtest_run_id ON experiment_trades(backtest_run_id);
CREATE INDEX idx_experiment_trades_entry_time ON experiment_trades(entry_time);
