- always add new files to git
- shared formatting utilities are in `src/main/resources/static/formatters.js` - use `formatNumber()`, `formatDate()`, and `formatDateTime()` across all HTML pages for consistent formatting
- use `showConfirmModal(title, message, confirmText, callback, isDanger)` from `js/components/modal.js` for confirmation dialogs instead of browser's `confirm()` - supports danger (red) and warning (orange) styles

## Architecture Notes
- Kotlin/Spring Boot backend with JPA entities in `model/` directory
- Async experiment execution uses `RunningAggregator` for memory-efficient streaming statistics
- Database migrations are in `src/main/resources/db/migration/` using Flyway (V1, V2, V3...)
- DTOs and entity extension functions are in `dto/ExperimentDtos.kt`
- Frontend is vanilla JS with Chart.js for visualizations
- Trades are saved to `experiment_trades` table only for experiments with ≤`tradesThreshold` backtests (configurable, default 100)

## Configuration
All runtime constants are centralized in `BacktestProperties` (`config/BacktestProperties.kt`) with nested config classes, configurable via `application.yml`:

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
- `TradingProcessor` - Single source of truth for trading logic (P&L, transaction costs, drawdown tracking)
- `TradingProcessorFactory` - Creates processor instances with independent `CoinFlipStrategy` for thread-safe parallel execution
- `TradingState` - Mutable state container (balance, positions, trades)
- `TradingConfig` - Reusable config from `BacktestProperties.trading`, passed to processor
- `BacktestEngine` uses `TradingProcessor` for backtesting, `SimulationService` uses it for step-by-step simulation