- always add new files to git
- shared formatting utilities are in `src/main/resources/static/formatters.js` - use `formatNumber()`, `formatDate()`, and `formatDateTime()` across all HTML pages for consistent formatting
- use `showConfirmModal(title, message, confirmText, callback, isDanger)` from `js/components/modal.js` for confirmation dialogs instead of browser's `confirm()` - supports danger (red) and warning (orange) styles

## Architecture Notes
- Kotlin/Spring Boot backend with JPA entities in `model/` directory
- Async experiment execution uses `RunningAggregator` for memory-efficient streaming statistics
- Database migrations are in `src/main/resources/db/migration/` using Flyway (V1, V2, V3...)
- DTOs and entity extension functions are in `dto/ExperimentDtos.kt`
- Frontend is vanilla JS with Chart.js for visualizations