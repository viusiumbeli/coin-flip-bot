# Coin-Flip Trading Bot

A comprehensive backtesting and live trading system implementing Van Tharp's famous coin-flip strategy. This project demonstrates that with proper risk management, exit strategies, and position sizing, even random entry signals can be profitable.

## Overview

Based on Van Tharp and Tom Basso's research from the 1990s, this system proves a counterintuitive point:

> **"Entry timing matters far less than traders believe. Exit strategy and position sizing determine profitability."**

The system uses **random 50/50 coin flips** to decide LONG or SHORT entries, combined with professional risk management (1% risk model) and ATR-based trailing stops. Results are compared against buy-and-hold to validate the strategy.

## Features

### Backtesting
Run single backtests on historical Binance data with instant results. View complete trade history, performance metrics, and comparison against buy-and-hold strategy.

### Monte Carlo Experiments
Run up to 10 million simulations to statistically validate the strategy. Experiments run asynchronously with progress tracking, and results include aggregated statistics (mean, median, standard deviation).

### Step-by-Step Simulation
Interactive candle-by-candle walkthrough for learning. Navigate forward/backward through time, see positions open and close, and understand exactly how the strategy behaves.

### Data Management
Sync historical OHLCV data from Binance. View data availability per symbol/timeframe and download missing candles on demand.

### Live Trading
Real-time execution against Binance WebSocket feed. Monitor open positions, track completed trades, and view balance history over time.

## The Coin Flip Strategy

### Entry Logic
- `CoinFlipStrategy.flipCoin()` returns random LONG or SHORT with 50% probability
- Entry attempts occur based on `entryFrequency` config (default: every 2 candles)
- New positions open only when: available capital exists AND position limit not reached

### Exit Logic (ATR Trailing Stop)
```
Initial Stop = entryPrice ± (atrMultiplier × ATR)
  LONG:  entryPrice - (3 × ATR)
  SHORT: entryPrice + (3 × ATR)

Trailing: Stop follows favorable price movement, never moves closer to entry
Exit: Position closes when price hits trailing stop
```

### Position Sizing (1% Risk Model)
```
Risk Amount = Account Balance × riskPerTrade (default 1%)
Position Size = Risk Amount / Stop Distance (entry to stop)
```

**Example**: Balance=$10,000, risk=1%, ATR=$100, multiplier=3
- Stop Distance = 3 × $100 = $300
- Risk Amount = $10,000 × 0.01 = $100
- Position Size = $100 / $300 = 0.333 units

This ensures **each losing trade loses exactly 1% of capital**.

### Transaction Costs
- 0.1% per side (0.2% round-trip)
- Applied at both entry and exit

## Web Interface

| Page | URL | Description |
|------|-----|-------------|
| **Backtest** | `/` | Run single backtests, view trade table and performance chart |
| **Experiments** | `/experiments.html` | Create Monte Carlo experiments, monitor progress, compare results |
| **Simulation** | `/simulation.html` | Interactive candle-by-candle learning with live position updates |
| **Data** | `/candles.html` | View data status and sync missing candles from Binance |
| **Live Trading** | `/live.html` | Real-time trading dashboard with active sessions |

## Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Framework | Spring Boot 3.x (WebFlux) |
| Database | PostgreSQL with R2DBC (async) |
| Data Source | Binance API (REST + WebSocket) |
| HTTP Client | Ktor |
| Frontend | Vanilla JS + Chart.js |
| Java Version | 21 |

## Quick Start

### Prerequisites
- JDK 21 or higher
- PostgreSQL 14+
- Gradle 8.x (or use included wrapper)

### Database Setup
```bash
# Create database
createdb coinflipbot

# Or via psql
psql -c "CREATE DATABASE coinflipbot;"
```

### Installation
```bash
# Clone the repository
git clone <repository-url>
cd coin-flip-bot

# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

### Access the Web UI
Open http://localhost:8080 in your browser.

### First Steps
1. Go to **Data** page (`/candles.html`)
2. Click "Sync All" to download historical data from Binance
3. Go to **Backtest** page (`/`) and run your first backtest
4. Explore **Experiments** for Monte Carlo analysis

## Configuration

All configuration is in `src/main/resources/application.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/coinflipbot
    username: postgres
    password: postgres

backtest:
  symbols:
    - BTCUSDT
    - ETHUSDT
    - BNBUSDT
  timeframes:
    - ONE_MINUTE
    - ONE_HOUR
    - FOUR_HOURS
    - ONE_DAY
  initial-capital: 10000
  start-date: 2020-01-01T00:00:00Z

  trading:                          # Core strategy parameters
    risk-per-trade: 1.0             # Risk 1% of capital per trade
    atr-period: 10                  # ATR calculation period
    atr-multiplier: 3.0             # Stop distance = 3 × ATR
    transaction-cost-percent: 0.1   # 0.1% per side
    max-concurrent-positions: 5     # Max open positions
    max-position-size-percent: 20   # Max single position size
    entry-frequency: 2              # Attempt entry every N candles

  experiment:                       # Monte Carlo limits
    sync-backtest-limit: 1000000    # Max sync experiment runs
    async-backtest-limit: 10000000  # Max async experiment runs

  async:                            # Parallelism settings
    parallelism-min: 4
    parallelism-max: 32
    channel-capacity: 1000
    batch-size: 1000

  api:                              # REST API settings
    max-page-size: 1000
    http-timeout-ms: 30000
    rate-limit-delay-ms: 0

live:
  enabled: true
  initial-capital: 10000
  reconnect-delay-ms: 5000
  max-reconnect-attempts: 10
  balance-snapshot-interval-minutes: 60
  websocket-url: wss://stream.binance.com:9443/ws
```

### Key Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `trading.risk-per-trade` | 1.0 | Percentage of capital risked per trade |
| `trading.atr-multiplier` | 3.0 | Stop distance as multiple of ATR |
| `trading.max-concurrent-positions` | 5 | Maximum open positions |
| `trading.entry-frequency` | 2 | Candles between entry attempts |
| `trading.transaction-cost-percent` | 0.1 | Fee per trade side |

## API Reference

### Backtest API (`/api/backtest`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/run` | Run single backtest, returns results immediately |
| GET | `/symbols` | List available symbols and timeframes |
| GET | `/config` | Get current backtest configuration |

### Experiments API (`/api/experiments`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | Create async experiment (returns 202) |
| GET | `/{id}/status` | Poll experiment progress |
| GET | `/{id}` | Get detailed experiment results |
| GET | `/{id}/runs` | Get paginated individual runs |
| POST | `/compare` | Compare multiple experiments |
| POST | `/{id}/cancel` | Cancel running experiment |
| DELETE | `/{id}` | Delete experiment |

### Simulation API (`/api/simulation`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/init` | Initialize step-by-step simulation |
| POST | `/next` | Advance to next candle |
| POST | `/previous` | Go back to previous candle |
| POST | `/reset` | Reset to initial state |
| GET | `/state` | Get current simulation state |

### Data API (`/api/candles`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/status` | Check data availability per symbol/timeframe |
| POST | `/sync` | Sync missing candles for one pair |
| POST | `/sync-all` | Sync all configured pairs |

### Live Trading API (`/api/live`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/sessions/start` | Start live trading session (returns 202) |
| POST | `/sessions/{id}/stop` | Stop a live session |
| GET | `/sessions` | List all trading sessions |
| GET | `/sessions/{id}` | Get session details with positions |
| GET | `/sessions/{id}/trades` | Get recent trades |
| GET | `/sessions/{id}/snapshots` | Get balance history |

## Bybit Trading Client (NEW)

The system now includes a Bybit trading client for executing real trades on the Bybit exchange.

### Supported Operations

| Operation | Method | Description |
|-----------|--------|-------------|
| Place Order | `placeOrder()` | Market/Limit orders with optional TP/SL |
| Cancel Order | `cancelOrder()` | Cancel unfilled orders |
| Amend Order | `amendOrder()` | Modify unfilled orders |
| Set TP/SL | `setTradingStop()` | Set/update stop loss and take profit |
| Set Leverage | `setLeverage()` | Configure position leverage |
| Get Positions | `getPositions()` | Query open positions |
| Get Balance | `getWalletBalance()` | Query account balance |

### Configuration

```yaml
live:
  exchange: BYBIT

  # API credentials (environment variables take precedence)
  bybit-api-key: ${BYBIT_API_KEY:}
  bybit-api-secret: ${BYBIT_API_SECRET:}
  bybit-demo: true  # true = demo (safe), false = mainnet (real money)
```

Or via environment variables:
```bash
export BYBIT_API_KEY=your_api_key
export BYBIT_API_SECRET=your_api_secret
```

### Demo vs Mainnet

| Environment | URL | Description |
|-------------|-----|-------------|
| Demo | `api-demo.bybit.com` | 50K USDT simulated funds, no real money |
| Mainnet | `api.bybit.com` | Real trading with real funds |

**Safety:** Demo mode is the default. Set `bybit-demo: false` only when ready for live trading.

## Project Structure

```
src/main/kotlin/com/trading/coinflip/
├── api/                    # REST Controllers
│   ├── backtest/          # Backtest endpoints
│   ├── candle/            # Data sync endpoints
│   ├── experiment/        # Monte Carlo endpoints
│   ├── simulation/        # Step-by-step endpoints
│   ├── live/              # Live trading endpoints
│   └── exception/         # Global error handling
├── backtest/              # Backtest execution
│   ├── BacktestEngine.kt
│   ├── BacktestService.kt
│   └── model/             # BacktestConfig, BacktestResult
├── candle/                # Data management
│   ├── CandleService.kt
│   ├── BinanceClient.kt
│   └── model/             # CandleEntity
├── common/                # Shared code
│   ├── config/            # BacktestProperties, TradingConfig
│   ├── dto/               # TradeDto
│   └── model/             # Timeframe enum
├── engine/                # Core trading logic
│   ├── TradingProcessor.kt    # Single source of truth for P&L
│   ├── CoinFlipStrategy.kt    # Random entry + position sizing
│   └── model/             # Position, Trade, TradingState
├── experiment/            # Monte Carlo simulations
│   ├── ExperimentService.kt
│   ├── AsyncExperimentExecutor.kt
│   ├── RunningAggregator.kt
│   └── model/             # ExperimentEntity
├── simulation/            # Step-by-step mode
│   └── SimulationService.kt
├── live/                  # Real-time trading
│   ├── LiveTradingService.kt
│   └── repository/        # Live data persistence
└── analytics/             # Performance metrics
    └── PerformanceAnalytics.kt
```

## Database Schema

### Core Tables

| Table | Purpose |
|-------|---------|
| `candles` | OHLCV data with pre-calculated ATR |
| `experiments` | Experiment config + aggregated results |
| `backtest_runs` | Individual backtest results within experiment |

### Live Trading Tables

| Table | Purpose |
|-------|---------|
| `live_sessions` | Active/historical trading sessions |
| `live_positions` | Open positions for active sessions |
| `live_trades` | Completed trades from live execution |
| `live_balance_snapshots` | Balance history for charting |

**Key Indexes**: `(symbol, timeframe, open_time)` on candles for fast range queries

**Note**: ATR is calculated by PostgreSQL `BEFORE INSERT` trigger, ensuring atomic calculation without JVM memory issues.

## Understanding Results

### Performance Metrics

| Metric | Description |
|--------|-------------|
| **Total Return %** | Overall profit/loss percentage |
| **Buy & Hold Return %** | What you'd make just holding the asset |
| **Win Rate** | Percentage of profitable trades |
| **Profit Factor** | Total wins / Total losses (>1 is profitable) |
| **Sharpe Ratio** | Risk-adjusted return (>1 is good) |
| **Max Drawdown %** | Largest peak-to-trough decline |

### Interpreting Results

- **Win rate ~40%** is normal for trend-following systems
- **Profit factor > 1.0** indicates overall profitability
- **Sharpe ratio > 1.0** suggests good risk-adjusted returns
- Compare against buy-and-hold to assess strategy value

## Key Findings

Based on Van Tharp's research and this implementation:

### What Works
- System can be profitable with random entries
- 1% risk model prevents catastrophic losses
- ATR trailing stops capture trends while limiting losses
- Diversification across markets improves results

### What Doesn't
- Usually underperforms simple buy-and-hold in strong bull markets
- Transaction costs significantly impact profitability
- Long drawdown periods test psychological endurance
- Win rate typically 35-45% (trend-following characteristic)

## Educational Purpose

This project is **for educational purposes only**:
- Demonstrates importance of risk management over entry timing
- Shows how to build proper backtesting infrastructure
- Illustrates realistic transaction costs and slippage
- Compares active trading vs passive investing

**Not recommended for live trading with real money** - this proves a theoretical point, not an optimal trading strategy.

## Code Quality

```bash
# Check code style
./gradlew ktlintCheck

# Auto-fix violations
./gradlew ktlintFormat

# Run tests
./gradlew test

# Full build
./gradlew build
```

ktlint enforces Kotlin code style and runs automatically on build.

## References

- Van Tharp, *Trade Your Way to Financial Freedom*
- Tom Basso's coin flip system experiments (1990s)
- StatOasis 2024-2025 coin flip study
- Malkiel, *A Random Walk Down Wall Street*

## Disclaimer

**IMPORTANT**: This is a research/educational tool demonstrating trading concepts. Past performance does not guarantee future results. The random entry system is designed to prove a theoretical point about risk management, not to be used as an actual trading strategy. Cryptocurrency trading carries significant risk of loss.

---

## License

This project is for educational purposes. Use at your own risk.
