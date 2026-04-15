- always add new files to git
- shared formatting utilities are in `src/main/resources/static/formatters.js` - use `formatNumber()`, `formatDate()`, and `formatDateTime()` across all HTML pages for consistent formatting
- use `showConfirmModal(title, message, confirmText, callback, isDanger)` from `js/components/modal.js` for confirmation dialogs instead of browser's `confirm()` - supports danger (red) and warning (orange) styles

## Package Structure
Feature-based organization under `com.trading.coinflip` with **one class per file**:
```
├── api/           # REST controllers
├── common/        # Shared code
│   ├── config/    # BacktestProperties, JacksonConfig
│   ├── dto/       # Shared DTOs only (TradeDto)
│   └── model/     # Domain models, JPA entities (CandleEntity, ExperimentEntity, etc.)
├── engine/        # Core trading (TradingProcessor, CoinFlipStrategy, ATRCalculator)
├── backtest/      # BacktestEngine, BacktestService, BacktestRequest, BacktestResponse
├── experiment/    # ExperimentService, AsyncExperimentExecutor + experiment DTOs
├── simulation/    # SimulationService + simulation DTOs
├── data/          # Repositories, BinanceClient, DataService + data DTOs
└── analytics/     # PerformanceAnalytics, ReportGenerator
```

**Naming Conventions:**
- `***Request` - API request bodies (e.g., `BacktestRequest`, `SyncRequest`)
- `***Response` - API responses (e.g., `BacktestResponse`, `SyncResponse`, `DataStatusResponse`)
- `***Entity` - JPA entities from DB (e.g., `CandleEntity`, `ExperimentEntity`, `BacktestRunEntity`)
- `***Config` - Configuration classes from application.yaml (e.g., `TradingConfig`, `AsyncConfig`)
- `***Dto` - Other data transfer objects (e.g., `TradeDto`, `ExperimentDetailDto`)

**DTOs live in their feature packages** (not common/dto/):
- `backtest/`: BacktestRequest, BacktestResponse
- `data/`: DataStatusResponse, SyncRequest, SyncResponse, AvailableSymbolsResponse
- `experiment/`: CreateExperimentRequest, ExperimentDetailDto, ExperimentMappers, etc.
- `simulation/`: SimulationInitRequest, SimulationStateDto, CandleDto, etc.
- `common/dto/`: TradeDto (shared between simulation and backtest)

## Architecture Notes
- Kotlin/Spring Boot backend with JPA entities in `common/model/`
- Async experiment execution uses `RunningAggregator` for memory-efficient streaming statistics
- Database migrations are in `src/main/resources/db/migration/` using Flyway (V1, V2, V3...)
- Entity extension functions (mappers) are in `experiment/ExperimentMappers.kt`
- Frontend is vanilla JS with Chart.js for visualizations
- Trades are saved to `experiment_trades` table only for experiments with ≤`tradesThreshold` backtests (configurable, default 100)

## Configuration
All runtime constants are centralized in `BacktestProperties` (`common/config/BacktestProperties.kt`) with nested config classes, configurable via `application.yml`:

```yaml
backtest:
  initial-capital: 2000
  trading:          # TradingConfig - reused in BacktestConfig
    risk-per-trade: 1.0
    atr-period: 10
    atr-multiplier: 3.0
    transaction-cost-percent: 0.1
    max-concurrent-positions: 5
    entry-frequency: 2
  experiment:       # ExperimentConfig
    sync-backtest-limit: 1000000
    async-backtest-limit: 10000000
    trades-threshold: 100
  async:            # AsyncConfig
    parallelism-min: 4
    parallelism-max: 32
    channel-capacity: 1000
    batch-size: 1000
  api:              # ApiConfig
    max-page-size: 1000
    http-timeout-ms: 30000
    rate-limit-delay-ms: 100
```

Access in code: `properties.trading.riskPerTrade`, `properties.experiment.tradesThreshold`, `properties.async.batchSize`, `properties.api.maxPageSize`

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
- `TradingState` (`engine/`) - Mutable state container (balance, positions, trades, counters)
- `CoinFlipStrategy` (`engine/`) - Random entry with ATR-based trailing stops
- `TradingConfig` - Reusable config from `BacktestProperties.trading`, passed to processor
- `BacktestEngine` (`backtest/`) uses `TradingProcessor` for backtesting
- `SimulationService` (`simulation/`) uses `TradingProcessor` for step-by-step simulation

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
- `Timeframe` uses `@JsonValue`/`@JsonCreator` for "1h", "4h", "1d" labels
- `Instant` uses custom serializer/deserializer for ISO-8601 format

Invalid input automatically returns 400 Bad Request.

## Controller Patterns
Controllers return objects directly without `ResponseEntity` wrappers. Exception handling is centralized in `GlobalExceptionHandler`:

```kotlin
// Good - return object directly, let exception handler manage errors
@GetMapping("/{id}")
fun getExperiment(@PathVariable id: Long): ExperimentDetailResponse =
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

## Summary
This codebase follows strict conventions: one class per file, feature-based package organization, and consistent naming (`*Request`, `*Response`, `*Entity`, `*Config`, `*Dto`). All configuration is centralized in `BacktestProperties`, ktlint enforces code style, and `TradingProcessor` is the single source of truth for trading logic. When adding new features, place classes in the appropriate feature package and follow existing patterns.