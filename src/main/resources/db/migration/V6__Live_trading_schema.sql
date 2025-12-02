-- Live trading session metadata
CREATE TABLE live_sessions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    timeframe VARCHAR(20) NOT NULL DEFAULT 'ONE_HOUR',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',

    -- Capital tracking
    initial_capital NUMERIC(20, 8) NOT NULL,
    current_balance NUMERIC(20, 8) NOT NULL,
    peak_balance NUMERIC(20, 8) NOT NULL,
    max_drawdown NUMERIC(20, 8) NOT NULL DEFAULT 0,

    -- Counters
    position_id_counter BIGINT NOT NULL DEFAULT 0,
    trade_id_counter BIGINT NOT NULL DEFAULT 0,

    -- ATR state for incremental calculation
    last_atr NUMERIC(20, 8),
    last_candle_close NUMERIC(20, 8),
    last_candle_time TIMESTAMP,

    -- Timestamps
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_update_at TIMESTAMP NOT NULL DEFAULT NOW(),
    stopped_at TIMESTAMP,

    -- Error tracking
    error_message TEXT,
    reconnect_count INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_live_sessions_symbol ON live_sessions(symbol);
CREATE INDEX idx_live_sessions_status ON live_sessions(status);

-- Open positions for live trading
CREATE TABLE live_positions (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    position_id BIGINT NOT NULL,

    symbol VARCHAR(20) NOT NULL,
    timeframe VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    -- Entry details
    entry_time TIMESTAMP NOT NULL,
    entry_price NUMERIC(20, 8) NOT NULL,
    position_size NUMERIC(20, 8) NOT NULL,

    -- Stop loss tracking
    initial_stop_loss NUMERIC(20, 8) NOT NULL,
    trailing_stop NUMERIC(20, 8) NOT NULL,
    highest_favorable_price NUMERIC(20, 8) NOT NULL,

    -- Capital allocation
    balance_before_open NUMERIC(20, 8) NOT NULL,
    balance_after_open NUMERIC(20, 8) NOT NULL,
    allocated_capital NUMERIC(20, 8) NOT NULL,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Exit data (populated when position closes)
    exit_time TIMESTAMP,
    exit_price NUMERIC(20, 8),
    exit_reason VARCHAR(100),
    profit_loss NUMERIC(20, 8),
    profit_loss_percent NUMERIC(20, 8),
    balance_before_close NUMERIC(20, 8),
    balance_after_close NUMERIC(20, 8),

    CONSTRAINT unique_position_in_session UNIQUE (session_id, position_id)
);

CREATE INDEX idx_live_positions_session_id ON live_positions(session_id);
CREATE INDEX idx_live_positions_status ON live_positions(status);

-- Completed trades for live trading
CREATE TABLE live_trades (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    trade_id BIGINT NOT NULL,

    symbol VARCHAR(20) NOT NULL,
    timeframe VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,

    -- Entry/Exit
    entry_time TIMESTAMP NOT NULL,
    entry_price NUMERIC(20, 8) NOT NULL,
    exit_time TIMESTAMP NOT NULL,
    exit_price NUMERIC(20, 8) NOT NULL,

    -- Size and P&L
    position_size NUMERIC(20, 8) NOT NULL,
    initial_stop_loss NUMERIC(20, 8) NOT NULL,
    trailing_stop NUMERIC(20, 8) NOT NULL,
    profit_loss NUMERIC(20, 8) NOT NULL,
    profit_loss_percent NUMERIC(20, 8) NOT NULL,
    exit_reason VARCHAR(100) NOT NULL,

    -- Balance tracking
    balance_before_open NUMERIC(20, 8) NOT NULL,
    balance_after_open NUMERIC(20, 8) NOT NULL,
    balance_before_close NUMERIC(20, 8) NOT NULL,
    balance_after_close NUMERIC(20, 8) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_trade_in_session UNIQUE (session_id, trade_id)
);

CREATE INDEX idx_live_trades_session_id ON live_trades(session_id);
CREATE INDEX idx_live_trades_entry_time ON live_trades(entry_time);

-- Balance history snapshots
CREATE TABLE live_balance_snapshots (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,

    balance NUMERIC(20, 8) NOT NULL,
    open_positions_count INT NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(20, 8) NOT NULL DEFAULT 0,
    candle_time TIMESTAMP NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_live_balance_snapshots_session_id ON live_balance_snapshots(session_id);
CREATE INDEX idx_live_balance_snapshots_candle_time ON live_balance_snapshots(candle_time);
