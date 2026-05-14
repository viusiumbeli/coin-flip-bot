# Van Tharp Coin-Flip Trading Bot

## Project Overview
This application implements **Van Tharp's coin-flip trading strategy** - an educational system that proves:

> **"Entry timing matters far less than traders believe. Exit strategy and position sizing determine profitability."**

The system uses **random 50/50 coin flips** to decide LONG or SHORT entries, combined with professional risk management (1% risk model) and ATR-based trailing stops. Results are compared against buy-and-hold to validate the strategy.

### Main Features
1. **Backtesting** (`/api/backtest`) - Run single backtests on historical Binance data
2. **Experiments** (`/api/experiments`) - Monte Carlo simulations (up to 10M runs) to statistically validate strategy
3. **Simulation** (`/api/simulation`) - Step-by-step candle-by-candle walkthrough for learning
4. **Data Sync** (`/api/candles`) - Sync historical OHLCV data from Binance
5. **Live Trading** (`/api/live`) - Real-time execution against Binance WebSocket feed

## The Coin Flip Strategy

### Entry Logic
- `CoinFlipStrategy.flipCoin()` returns random LONG/SHORT with 50% probability
- Entry attempts occur based on `entryFrequency` config (default: every 2 candles)
- New positions open only when: available capital exists AND position limit not reached

### Exit Logic (ATR Trailing Stop)
- **Initial Stop**: `entryPrice ± (atrMultiplier × ATR)` based on position side
  - LONG: `entryPrice - (3 × ATR)`
  - SHORT: `entryPrice + (3 × ATR)`
- **Trailing**: Stop follows favorable price movement, never moves closer to entry
- **Exit**: Position closes when price hits trailing stop

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

## Core Business Rules
- **Risk Management**: Max 1% loss per trade via position sizing formula
- **Diversification**: Up to `maxConcurrentPositions` (default 5) open trades
- **Transaction Costs**: 0.1% per side (0.2% round-trip), applied at entry and exit
- **No Profit Targets**: Trailing stops only - let winners run
- **Baseline Comparison**: Every backtest includes buy-and-hold return for comparison
- **Drawdown Tracking**: Peak-to-trough decline tracked for psychology/risk metrics

## Database Schema

### Core Tables
| Table | Purpose |
|-------|---------|
| `candles` | OHLCV data from Binance with pre-calculated ATR |
| `experiments` | Experiment config + aggregated results across all runs |
| `backtest_runs` | Individual backtest results within an experiment |

### Live Trading Tables
| Table | Purpose |
|-------|---------|
| `live_sessions` | Active/historical trading sessions with state and last_candle_id FK |
| `live_positions` | Open positions for active sessions |
| `live_trades` | Completed trades from live execution |
| `live_balance_snapshots` | Point-in-time balance history for charting |

**Key Indexes**: `(symbol, timeframe, open_time)` on candles for fast range queries

**ATR Calculation**: Computed by PostgreSQL `BEFORE INSERT` trigger on `candles` table (see `V9__ATR_database_trigger.sql`)

## API Endpoints

### Backtest (`/api/backtest`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/run` | Run single backtest, returns results immediately |
| GET | `/symbols` | List available symbols and timeframes |
| GET | `/config` | Get current backtest configuration |

### Candles (`/api/candles`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/status` | Check data availability per symbol/timeframe |
| POST | `/sync` | Sync missing candles for one symbol/timeframe |
| POST | `/sync-all` | Sync all configured symbol/timeframe combinations |

### Experiments (`/api/experiments`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/` | Create async experiment (returns 202) |
| GET | `/` | List all experiments |
| GET | `/{id}` | Get detailed experiment results |
| GET | `/{id}/status` | Poll experiment progress |
| GET | `/{id}/runs` | Get paginated individual runs |
| GET | `/runs/{runId}` | Get single backtest run with trades |
| POST | `/{id}/cancel` | Cancel running experiment |
| POST | `/compare` | Compare multiple experiments |
| DELETE | `/{id}` | Delete experiment (returns 204) |

### Simulation (`/api/simulation`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/init` | Initialize step-by-step simulation |
| POST | `/next` | Advance to next candle |
| POST | `/previous` | Go back to previous candle |
| POST | `/reset` | Reset to initial state |
| GET | `/state` | Get current simulation state |

### Live Trading (`/api/live`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/config` | Get live trading configuration |
| GET | `/sessions` | List all trading sessions |
| GET | `/sessions/{id}` | Get session details with open positions |
| GET | `/sessions/{id}/trades` | Get recent trades (with limit param) |
| GET | `/sessions/{id}/snapshots` | Get balance snapshot history |
| POST | `/sessions/start` | Start live trading session (returns 202) |
| POST | `/sessions/{id}/stop` | Stop a live session |

## Frontend Pages

| Page | URL | Purpose |
|------|-----|---------|
| `index.html` | `/` | Single backtest runner with trade table |
| `experiments.html` | `/experiments.html` | Create/compare Monte Carlo experiments |
| `simulation.html` | `/simulation.html` | Interactive candle-by-candle learning |
| `candles.html` | `/candles.html` | Sync and manage historical data |
| `live.html` | `/live.html` | Live trading dashboard |

## Common Mistakes to Avoid

1. **Don't modify TradingProcessor without understanding the full flow** - It's the single source of truth used by backtest, simulation, AND live trading
2. **Don't create entities without Flyway migration** - All schema changes go through `db/migration/V*.sql`
3. **Don't add BigDecimal division in hot paths** - Use lazy pre-computed rates in `TradingConfig` (e.g., `riskPerTradeRate`)
4. **Don't use blocking calls in suspend functions** - No `runBlocking`, use `Mutex.withLock` not `@Synchronized`
5. **Don't forget async cancellation handling** - Check `isActive` in loops, use `SupervisorJob` for isolation
6. **Don't use browser `confirm()`** - Use `showConfirmModal()` from `modal.js`
7. **Don't apply timezone to dates** - Use `toUTCISOString()` from `formatters.js`, all dates are UTC
8. **Don't use ATRCalculator for new code** - ATR is calculated by database trigger; re-fetch candle after save to get ATR value

---

## Quick Reference

- Always add new files to git
- Shared formatting utilities are in `src/main/resources/static/formatters.js` - use `formatNumber()`, `formatDate()`, and `formatDateTime()` across all HTML pages
- Use `showConfirmModal(title, message, confirmText, callback, isDanger)` from `js/components/modal.js` for confirmation dialogs instead of browser's `confirm()`

## Package Structure
Feature-based organization under `com.trading.coinflip` with **one class per file**:
```
├── api/                    # REST controllers + request/response DTOs
│   ├── backtest/          # BacktestController, BacktestRequest, BacktestResponse
│   ├── candle/            # CandleController, SyncRequest, CandleStatusResponse
│   ├── experiment/        # ExperimentController + all experiment DTOs
│   ├── simulation/        # SimulationController, SimulationInitRequest
│   ├── live/              # LiveController + all live trading DTOs
│   └── exception/         # GlobalExceptionHandler, NotFoundException, BadRequestException
├── common/
│   ├── config/            # BacktestProperties, LiveProperties, R2dbcConfig, JacksonConfig
│   ├── dto/               # TradeDto (shared between simulation and backtest)
│   ├── model/             # Timeframe enum
│   └── util/              # ReentrantMutex
├── engine/                # Core trading logic
│   ├── TradingProcessor.kt    # Single source of truth for P&L, costs, drawdown
│   ├── CoinFlipStrategy.kt    # Random entry + position sizing + trailing stops
│   └── model/             # Position, Trade, TradingState, TradingEvent
├── backtest/
│   ├── BacktestEngine.kt
│   ├── BacktestService.kt
│   └── model/             # BacktestConfig, BacktestResult, BacktestRunEntity
├── candle/                # Data management (renamed from data/)
│   ├── CandleService.kt       # Sync, load, stats
│   ├── BinanceClient.kt       # REST API client for historical data
│   └── model/             # CandleEntity, CandleStats
│   └── repository/        # CandleRepository
├── experiment/
│   ├── ExperimentService.kt
│   ├── AsyncExperimentExecutor.kt
│   ├── RunningAggregator.kt
│   ├── BatchPersistenceService.kt
│   └── model/             # ExperimentEntity, ExperimentStatus
│   └── repository/        # ExperimentRepository, BacktestRunBulkRepository
├── simulation/
│   └── SimulationService.kt
├── live/                  # Real-time trading
│   ├── LiveTradingService.kt
│   ├── BinanceWebSocketClient.kt  # WebSocket for real-time prices
│   ├── LiveTradingStateHolder.kt
│   ├── LiveStateRecoveryService.kt
│   ├── LiveMappers.kt
│   └── model/             # LiveSessionEntity, LiveTradeEntity, LivePositionEntity, LiveBalanceSnapshotEntity
│   └── repository/        # LiveSessionRepository, LiveTradeRepository, etc.
└── analytics/
    └── PerformanceAnalytics.kt
```

## Naming Conventions
- `***Request` - API request bodies (e.g., `BacktestRequest`, `SyncRequest`)
- `***Response` - API responses (e.g., `BacktestResponse`, `CandleStatusResponse`)
- `***Entity` - R2DBC entities from DB (e.g., `CandleEntity`, `ExperimentEntity`)
- `***Config` - Configuration classes (e.g., `TradingConfig`, `AsyncConfig`)
- `***Dto` - Other data transfer objects (e.g., `TradeDto`)

**DTOs live in `api/` subpackages**, co-located with their controllers:
- `api/backtest/`: BacktestRequest, BacktestResponse
- `api/candle/`: SyncRequest, SyncResponse, CandleStatusResponse, AvailableSymbolsResponse
- `api/experiment/`: CreateExperimentRequest, ExperimentDetailResponse, ExperimentStatusResponse, etc.
- `api/simulation/`: SimulationInitRequest, SimulationStateResponse
- `api/live/`: StartSessionRequest, LiveSessionDetailResponse, LiveTradeResponse, etc.
- `common/dto/`: TradeDto (shared between features)

## Architecture Notes
- Kotlin/Spring Boot backend with R2DBC for async database access
- Async experiment execution uses `RunningAggregator` for memory-efficient streaming statistics
- Database migrations are in `src/main/resources/db/migration/` using Flyway (V1-V11)
- Frontend is vanilla JS with Chart.js for visualizations

## Configuration
All runtime constants are centralized in `BacktestProperties` and `LiveProperties`, configurable via `application.yml`:

```yaml
backtest:
  symbols: [BTCUSDT, ETHUSDT, BNBUSDT]
  timeframes: [ONE_MINUTE, ONE_HOUR, FOUR_HOURS, ONE_DAY]
  initial-capital: 10000
  start-date: 2020-01-01T00:00:00Z

  trading:                          # TradingConfig
    risk-per-trade: 1.0
    atr-period: 10
    atr-multiplier: 3.0
    transaction-cost-percent: 0.1
    max-concurrent-positions: 5
    max-position-size-percent: 20
    entry-frequency: 2

  experiment:                       # ExperimentConfig
    sync-backtest-limit: 1000000
    async-backtest-limit: 10000000

  async:                            # AsyncConfig
    parallelism-min: 4
    parallelism-max: 32
    channel-capacity: 1000
    batch-size: 1000
    shutdown-timeout-ms: 30000
    progress-log-interval: 10000

  api:                              # ApiConfig
    max-page-size: 1000
    http-timeout-ms: 30000
    rate-limit-delay-ms: 0

live:                               # LiveProperties
  enabled: true
  symbols: [BTCUSDT, ETHUSDT, BNBUSDT]
  initial-capital: 10000
  reconnect-delay-ms: 5000
  max-reconnect-attempts: 10
  balance-snapshot-interval-minutes: 60
  heartbeat-interval-ms: 30000
  websocket-url: wss://stream.binance.com:9443/ws
```

Access in code: `properties.trading.riskPerTrade`, `liveProperties.reconnectDelayMs`

## Logging
Use `KotlinLogging` with logger declared **inside the class** as the first property:
```kotlin
@Service
class SomeService(...) {

    private val log = KotlinLogging.logger {}

    // rest of class
}
```

## Code Style
ktlint enforces Kotlin code style. Runs automatically on build.
- `./gradlew ktlintCheck` - Check for violations
- `./gradlew ktlintFormat` - Auto-fix violations

## Trading Architecture
All trading components are Spring `@Component` beans with constructor injection:
- `TradingProcessor` (`engine/`) - Single source of truth for trading logic (P&L, transaction costs, drawdown tracking)
- `TradingState` (`engine/model/`) - Mutable state container (balance, positions, trades, counters)
- `CoinFlipStrategy` (`engine/`) - Random entry with ATR-based trailing stops
- `TradingConfig` - Reusable config from `BacktestProperties.trading`, passed to processor
- `BacktestEngine` (`backtest/`) uses `TradingProcessor` for backtesting
- `SimulationService` (`simulation/`) uses `TradingProcessor` for step-by-step simulation
- `LiveTradingService` (`live/`) uses `TradingProcessor` for real-time trading

### Live Trading Components
- `BinanceWebSocketClient` - Connects to Binance WebSocket for real-time kline data
- `LiveTradingStateHolder` - Manages current session state in memory
- `LiveStateRecoveryService` - Recovers state after crashes from database
- `LiveMappers` - Extension functions for entity-to-DTO conversion

## Request DTO Types
Use domain types directly in request DTOs instead of Strings to eliminate manual parsing:

```kotlin
// Good - Jackson handles deserialization automatically
data class BacktestRequest(
    val symbol: String,
    val timeframe: Timeframe,      // Not String
    val startDate: Instant? = null, // Not String?
    val endDate: Instant? = null,
)

// Bad - requires manual parsing in every service
val timeframe = Timeframe.fromLabel(request.timeframe) ?: throw ...
val startDate = Instant.parse(request.startDate)
```

**Configured in `JacksonConfig`:**
- `Timeframe` uses `@JsonValue`/`@JsonCreator` for "1m", "1h", "4h", "1d" labels
- `Instant` uses custom serializer/deserializer for ISO-8601 format

**Configured in `R2dbcConfig`:**
- Custom converters for enum types: `Timeframe`, `ExperimentStatus`, `PositionSide`, `PositionStatus`, `LiveSessionStatus`

Invalid input automatically returns 400 Bad Request.

## Controller Patterns
Controllers return objects directly without `ResponseEntity` wrappers. Exception handling is centralized in `GlobalExceptionHandler`:

```kotlin
// Good - return object directly, let exception handler manage errors
@GetMapping("/{id}")
suspend fun getExperiment(@PathVariable id: Long): ExperimentDetailResponse =
    experimentService.getExperiment(id)

// Bad - manual ResponseEntity and try-catch
@GetMapping("/{id}")
fun getExperiment(@PathVariable id: Long): ResponseEntity<ExperimentDetailResponse> =
    try {
        ResponseEntity.ok(experimentService.getExperiment(id))
    } catch (e: Exception) {
        ResponseEntity.notFound().build()
    }
```

**Status codes:**
- `200 OK` - Default for successful responses (automatic)
- `202 ACCEPTED` - Use `@ResponseStatus(HttpStatus.ACCEPTED)` for async operations
- `204 NO_CONTENT` - Use `@ResponseStatus(HttpStatus.NO_CONTENT)` for delete operations
- `400/404/500` - Handled by `GlobalExceptionHandler` via exceptions

**Custom exceptions** (`api/exception/`):
- `NotFoundException` - Returns 404 Not Found
- `BadRequestException` - Returns 400 Bad Request
- `IllegalArgumentException`/`IllegalStateException` - Also return 400

**CORS**: All controllers use `@CrossOrigin(origins = ["*"])`

## Coroutines Architecture
The codebase uses Kotlin coroutines throughout with Spring WebFlux for non-blocking HTTP handling.

**Key patterns:**
- Controllers use `suspend fun` for async endpoints (enabled by `spring-boot-starter-webflux`)
- Services use `suspend fun` for operations involving I/O or async calls
- Use `Mutex.withLock {}` instead of `@Synchronized` for coroutine-safe locking
- Use `Channel<T>` for producer-consumer patterns (e.g., backtest results streaming)
- Use `Semaphore` with `withPermit` for parallelism control

**Coroutine components:**
- `AsyncExperimentExecutor` - Uses `CoroutineScope` with `SupervisorJob` for parallel backtest execution
- `BatchPersistenceService` - Consumes from `Channel` using suspend functions
- `RunningAggregator` - Uses `Mutex` for thread-safe statistics aggregation
- `SimulationService` - Uses `Mutex` for state synchronization
- `BinanceClient` - Uses Ktor's suspend-based HTTP client
- `BinanceWebSocketClient` - Uses Ktor's WebSocket client for live data
- `CandleService` - Native suspend functions for data fetching
- `LiveTradingService` - Uses `Mutex` and coroutines for real-time trading

**Utilities:**
- `ReentrantMutex` (`common/util/`) - Kotlin coroutine extension for reentrant mutex locking

**Guidelines:**
```kotlin
// Good - suspend function with Mutex
suspend fun add(result: BacktestResult) = mutex.withLock {
    // thread-safe operations
}

// Bad - blocking @Synchronized in coroutine context
@Synchronized
fun add(result: BacktestResult) { ... }

// Good - call suspend functions directly
suspend fun loadData() {
    val data = binanceClient.fetchData() // suspend call
}

// Bad - wrapping suspend in runBlocking
fun loadData() = runBlocking {
    binanceClient.fetchData()
}
```

## Frontend Patterns

**Date/Time Handling (UTC only):**
- All `datetime-local` inputs use `max="9999-12-31T23:59"` to limit year to 4 digits
- Use `toUTCISOString(value)` from `formatters.js` when sending dates to API - treats input as UTC
- Use `formatDate()` and `formatDateTime()` for displaying ISO strings from backend
- Never use `new Date(datetimeLocalValue).toISOString()` - causes timezone shift

```javascript
// Good - treats datetime-local value as UTC
startDate: toUTCISOString(document.getElementById('startDate').value)

// Bad - applies local timezone offset
startDate: new Date(startDate).toISOString()
```

## Data Layer Architecture

**Separation of Concerns:**
- **Data sync** (`/api/candles/sync`) - Downloads from Binance API, saves page-by-page
- **Backtests/Experiments** - Read-only from database, never download

**Key Components:**
- `BinanceClient.streamHistoricalData()` - Returns `Flow<List<CandleEntity>>`, emits pages of 1000 candles
- `CandleService.syncMissingData()` - Syncs from latest candle to now
- `CandleService.loadCandlesParallel()` - Parallel page loading for large datasets
- `CandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc()` - DB-only fetch

**Data Flow:**
```
Binance API → BinanceClient (streaming) → CandleRepository (page-by-page save) → DB
                                                                                   ↓
Backtest/Experiment ← CandleRepository ←───────────────────────────────────────────┘
```

**ATR Calculation (Database Trigger):**
- ATR is calculated atomically by PostgreSQL `BEFORE INSERT` trigger on `candles` table
- See `V9__ATR_database_trigger.sql` for the `calculate_atr_for_candle()` function
- Ensures data consistency - ATR is set in the same transaction as INSERT
- No memory issues - no need to load millions of rows into JVM
- Period hardcoded to 10 in trigger (matches `trading.atr-period` default)
- `ATRCalculator.kt` is deprecated - kept for reference only

## Summary
This codebase follows strict conventions: one class per file, feature-based package organization, and consistent naming (`*Request`, `*Response`, `*Entity`, `*Config`, `*Dto`). All configuration is centralized in `BacktestProperties` and `LiveProperties`, ktlint enforces code style, and `TradingProcessor` is the single source of truth for trading logic. The async layer uses Kotlin coroutines with Spring WebFlux, `Mutex` for synchronization, and `Channel` for streaming. Frontend uses vanilla JS with UTC-only date handling via `formatters.js` utilities. When adding new features, place classes in the appropriate feature package and follow existing patterns.

## Conclusion
This project demonstrates Van Tharp's core insight: **you can be profitable with random entries if your risk management is sound**. The coin flip removes all pretense of market prediction, forcing focus on what actually matters - position sizing that limits losses to 1% per trade and trailing stops that let winners run.

The technical implementation prioritizes correctness over cleverness. `TradingProcessor` handles all P&L calculations in one place. BigDecimal ensures no floating-point errors corrupt financial data. Coroutines enable running millions of Monte Carlo simulations without blocking. The result is a system where you can statistically prove whether a risk management approach works, independent of entry strategy.

Use this codebase to experiment with different ATR multipliers, position limits, and risk percentages. The numbers don't lie - and that's the point.
