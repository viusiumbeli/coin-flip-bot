# Coin-Flip Trading Bot - Backtesting System

A comprehensive backtesting system implementing Van Tharp's famous coin-flip trading strategy. This project demonstrates that with proper risk management, exit strategies, and position sizing, even random entry signals can be profitable.

## About the Strategy

Based on Van Tharp and Tom Basso's research from the 1990s, this system proves a counterintuitive point:

> **Entry timing matters far less than traders believe. Exit strategy and position sizing determine profitability.**

### Key Features

- **Random Entry**: Uses coin flip (50/50 random) to decide LONG or SHORT positions
- **ATR-Based Trailing Stops**: 3x ATR trailing stop from entry price (Van Tharp's method)
- **1% Risk Model**: Risk only 1% of capital per trade for survival through drawdowns
- **Multi-Market**: Test across multiple cryptocurrencies and timeframes
- **Comprehensive Analytics**: Sharpe ratio, win rate, profit factor, max drawdown, etc.
- **Buy & Hold Comparison**: Every backtest compares against simple buy-and-hold strategy

## Results You'll See

The system will show you:
- Whether random entry + good risk management beats buy-and-hold
- How important exit strategies and position sizing really are
- Performance across different market conditions and timeframes
- Real transaction costs impact on profitability

## Technical Stack

- **Language**: Kotlin
- **Framework**: Spring Boot 3.x
- **Database**: H2 (embedded, file-based)
- **Data Source**: Binance API (free historical data)
- **Java Version**: 21

## Project Structure

```
src/main/kotlin/com/trading/coinflip/
├── model/              # Data models (Candle, Trade, Position, etc.)
├── data/               # Data fetching and persistence
│   ├── BinanceClient.kt
│   ├── CandleRepository.kt
│   └── DataService.kt
├── strategy/           # Trading strategy logic
│   ├── CoinFlipStrategy.kt
│   └── ATRCalculator.kt
├── backtesting/        # Backtest engine
│   └── BacktestEngine.kt
├── analytics/          # Performance metrics
│   ├── PerformanceAnalytics.kt
│   └── ReportGenerator.kt
├── config/             # Configuration
│   └── BacktestProperties.kt
└── BacktestRunner.kt   # Main orchestrator
```

## Getting Started

### Prerequisites

- JDK 21 or higher
- Gradle 8.x (or use included wrapper)
- Internet connection (for fetching historical data)

### Installation

1. Clone the repository:
```bash
cd /Users/visiumbeli/projects/coin-flip-bot
```

2. Build the project:
```bash
./gradlew build
```

### Configuration

Edit `src/main/resources/application.yml` to configure:

```yaml
backtest:
  symbols:
    - BTCUSDT
    - ETHUSDT
    - BNBUSDT
  timeframes:
    - ONE_HOUR
    - FOUR_HOURS
    - ONE_DAY
  initial-capital: 10000
  risk-per-trade: 1.0           # 1% risk per trade
  atr-period: 10                # ATR calculation period
  atr-multiplier: 3.0           # Stop distance = 3 * ATR
  transaction-cost-percent: 0.1  # 0.1% per trade
  max-concurrent-positions: 5    # Max open positions
  start-date: 2020-01-01T00:00:00Z
```

### Running Backtests

```bash
./gradlew bootRun
```

The system will:
1. Download historical data from Binance (cached locally)
2. Calculate ATR for all candles
3. Run backtests for each symbol/timeframe combination
4. Generate detailed reports
5. Export results to `backtest_results.csv`

## Understanding the Results

### Performance Metrics

- **Total Return %**: Overall profit/loss percentage
- **Buy & Hold Return %**: What you'd make just holding the asset
- **Win Rate**: Percentage of profitable trades
- **Profit Factor**: (Total Wins / Total Losses) - higher is better
- **Sharpe Ratio**: Risk-adjusted return - above 1.0 is good
- **Max Drawdown %**: Largest peak-to-trough decline

### Sample Output

```
================================================================================
BACKTEST RESULTS
--------------------------------------------------------------------------------
Symbol:           BTCUSDT
Timeframe:        1d
Period:           2020-01-01 to 2024-11-20
--------------------------------------------------------------------------------
Initial Capital:  $10000.00
Final Capital:    $12450.00
Total Return:     $2450.00 (24.50%)
Buy & Hold Return: $15200.00 (152.00%)
--------------------------------------------------------------------------------
Total Trades:     87
Winning Trades:   35
Losing Trades:    52
Win Rate:         40.23%
Profit Factor:    1.45
Sharpe Ratio:     0.82
--------------------------------------------------------------------------------
vs Buy & Hold:    UNDERPERFORMED by 127.50%
--------------------------------------------------------------------------------
```

## Key Findings (Expected)

Based on Van Tharp's research and modern validations:

✅ **What Works:**
- System can be profitable with random entries
- 1% risk model prevents catastrophic losses
- ATR trailing stops capture trends while limiting losses
- Diversification across markets improves results

❌ **What Doesn't:**
- Usually underperforms simple buy-and-hold
- Transaction costs significantly impact profitability
- Long drawdown periods test psychological endurance
- Win rate typically 35-45% (trend-following characteristic)

## Educational Purpose

This project is **for educational purposes only**:
- Demonstrates importance of risk management over entry timing
- Shows how to build proper backtesting infrastructure
- Illustrates realistic transaction costs and slippage
- Compares active trading vs passive investing

**Not recommended for live trading** - this proves a theoretical point, not an optimal trading strategy.

## Customization

### Adding New Markets

Edit `application.yml`:
```yaml
symbols:
  - BTCUSDT
  - ETHUSDT
  - YOUR_SYMBOL_HERE
```

### Changing Risk Parameters

```yaml
risk-per-trade: 2.0      # Risk 2% per trade (more aggressive)
atr-multiplier: 4.0      # Wider stops (catch bigger trends)
max-concurrent-positions: 10  # More diversification
```

### Testing Different Time Periods

```yaml
start-date: 2017-01-01T00:00:00Z  # Test from 2017
end-date: 2023-12-31T23:59:59Z    # Until end of 2023
```

## Data Management

Historical data is cached in `./data/coinflipbot.mv.db` (H2 database).

To refresh data:
1. Delete the `data/` directory
2. Run the application again

## Extending the Project

### Ideas for Enhancement:

1. **Multiple Random Seeds**: Run 100+ backtests with different random seeds
2. **Monte Carlo Analysis**: Simulate thousands of random equity curves
3. **Walk-Forward Optimization**: Test on rolling time windows
4. **Alternative Exit Strategies**: Fixed profit targets, time-based exits
5. **Market Filters**: Only trade during trending conditions
6. **Equity Curve Trading**: Reduce size during drawdowns

## References

- Van Tharp, "Trade Your Way to Financial Freedom"
- Tom Basso's coin flip system experiments (1990s)
- StatOasis 2024-2025 coin flip study
- Malkiel, "A Random Walk Down Wall Street"

## License

This project is for educational purposes. Use at your own risk.

## Disclaimer

**IMPORTANT**: This is a research/educational tool demonstrating trading concepts. Past performance does not guarantee future results. The random entry system is designed to prove a theoretical point about risk management, not to be used as an actual trading strategy. Cryptocurrency trading carries significant risk of loss.

---

## Contact

For questions or improvements, feel free to open an issue or submit a pull request.
