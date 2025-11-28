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
All runtime constants are centralized in `BacktestProperties` (`config/BacktestProperties.kt`) and configurable via `application.yml`:
- **Strategy**: `entry-frequency` (trade entry probability per candle)
- **Experiment limits**: `sync-backtest-limit`, `async-backtest-limit`, `trades-threshold`
- **Async execution**: `parallelism-min/max`, `channel-capacity`, `batch-size`, `shutdown-timeout-ms`, `progress-log-interval`
- **API**: `max-page-size`, `http-timeout-ms`, `rate-limit-delay-ms`

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