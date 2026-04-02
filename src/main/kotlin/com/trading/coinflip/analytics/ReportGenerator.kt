package com.trading.coinflip.analytics

import com.trading.coinflip.model.BacktestResult
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.io.File
import java.math.RoundingMode

private val log = KotlinLogging.logger {}

@Component
class ReportGenerator {

    fun printResult(result: BacktestResult) {
        log.info { "\n" + "-".repeat(80) }
        log.info { "BACKTEST RESULTS" }
        log.info { "-".repeat(80) }
        log.info { "Symbol:           ${result.config.symbol}" }
        log.info { "Timeframe:        ${result.config.timeframe.label}" }
        log.info { "Period:           ${result.startDate} to ${result.endDate}" }
        log.info { "-".repeat(80) }
        log.info { "Initial Capital:  $${result.initialCapital.setScale(2, RoundingMode.HALF_UP)}" }
        log.info { "Final Capital:    $${result.finalCapital.setScale(2, RoundingMode.HALF_UP)}" }
        log.info { "Total Return:     $${result.totalReturn.setScale(2, RoundingMode.HALF_UP)} (${result.totalReturnPercent}%)" }
        log.info { "Buy & Hold Return: $${result.buyAndHoldReturn.setScale(2, RoundingMode.HALF_UP)} (${result.buyAndHoldReturnPercent}%)" }
        log.info { "-".repeat(80) }
        log.info { "Total Trades:     ${result.totalTrades}" }
        log.info { "Winning Trades:   ${result.winningTrades}" }
        log.info { "Losing Trades:    ${result.losingTrades}" }
        log.info { "Win Rate:         ${result.winRate}%" }
        log.info { "Profit Factor:    ${result.profitFactor}" }
        log.info { "Sharpe Ratio:     ${result.sharpeRatio}" }
        log.info { "-".repeat(80) }
        log.info { "Average Win:      $${result.averageWin.setScale(2, RoundingMode.HALF_UP)}" }
        log.info { "Average Loss:     $${result.averageLoss.setScale(2, RoundingMode.HALF_UP)}" }
        log.info { "Largest Win:      $${result.largestWin.setScale(2, RoundingMode.HALF_UP)}" }
        log.info { "Largest Loss:     $${result.largestLoss.setScale(2, RoundingMode.HALF_UP)}" }
        log.info { "-".repeat(80) }
        log.info { "Max Drawdown:     $${result.maxDrawdown.setScale(2, RoundingMode.HALF_UP)} (${result.maxDrawdownPercent}%)" }
        log.info { "Avg Trade Duration: ${result.averageTradeDuration} minutes" }
        log.info { "-".repeat(80) }

        // Performance vs Buy & Hold
        val outperformance = result.totalReturnPercent - result.buyAndHoldReturnPercent
        val outperformanceStr = if (outperformance > 0.toBigDecimal()) {
            "OUTPERFORMED by ${outperformance.setScale(2, RoundingMode.HALF_UP)}%"
        } else {
            "UNDERPERFORMED by ${outperformance.abs().setScale(2, RoundingMode.HALF_UP)}%"
        }
        log.info { "vs Buy & Hold:    $outperformanceStr" }
        log.info { "-".repeat(80) }
    }

    fun printComparisonReport(results: List<BacktestResult>) {
        if (results.isEmpty()) return

        log.info { "\n" }
        log.info { String.format("%-15s %-10s %12s %12s %12s %10s %10s %10s",
            "Symbol", "Timeframe", "Return %", "B&H %", "Trades", "Win %", "Sharpe", "Max DD %") }
        log.info { "-".repeat(100) }

        for (result in results) {
            log.info {
                String.format("%-15s %-10s %11.2f%% %11.2f%% %12d %9.2f%% %10.2f %9.2f%%",
                    result.config.symbol,
                    result.config.timeframe.label,
                    result.totalReturnPercent.toDouble(),
                    result.buyAndHoldReturnPercent.toDouble(),
                    result.totalTrades,
                    result.winRate.toDouble(),
                    result.sharpeRatio.toDouble(),
                    result.maxDrawdownPercent.toDouble()
                )
            }
        }

        log.info { "-".repeat(100) }

        // Summary statistics
        val avgReturn = results.map { it.totalReturnPercent.toDouble() }.average()
        val avgBuyHold = results.map { it.buyAndHoldReturnPercent.toDouble() }.average()
        val avgWinRate = results.map { it.winRate.toDouble() }.average()
        val avgSharpe = results.map { it.sharpeRatio.toDouble() }.average()

        log.info { "\nAVERAGE ACROSS ALL TESTS:" }
        log.info { String.format("Return: %.2f%% | Buy&Hold: %.2f%% | Win Rate: %.2f%% | Sharpe: %.2f",
            avgReturn, avgBuyHold, avgWinRate, avgSharpe) }

        val outperformedCount = results.count { it.totalReturnPercent > it.buyAndHoldReturnPercent }
        log.info { "\nOutperformed Buy & Hold: $outperformedCount / ${results.size} (${outperformedCount * 100 / results.size}%)" }
    }

    fun exportResultsToCsv(results: List<BacktestResult>, filename: String) {
        try {
            val file = File(filename)
            file.bufferedWriter().use { writer ->
                // Header
                writer.write("Symbol,Timeframe,StartDate,EndDate,InitialCapital,FinalCapital,")
                writer.write("TotalReturn,TotalReturnPercent,BuyAndHoldReturn,BuyAndHoldReturnPercent,")
                writer.write("TotalTrades,WinningTrades,LosingTrades,WinRate,ProfitFactor,SharpeRatio,")
                writer.write("AverageWin,AverageLoss,LargestWin,LargestLoss,MaxDrawdown,MaxDrawdownPercent,")
                writer.write("AvgTradeDuration\n")

                // Data rows
                for (result in results) {
                    writer.write("${result.config.symbol},")
                    writer.write("${result.config.timeframe.label},")
                    writer.write("${result.startDate},")
                    writer.write("${result.endDate},")
                    writer.write("${result.initialCapital},")
                    writer.write("${result.finalCapital},")
                    writer.write("${result.totalReturn},")
                    writer.write("${result.totalReturnPercent},")
                    writer.write("${result.buyAndHoldReturn},")
                    writer.write("${result.buyAndHoldReturnPercent},")
                    writer.write("${result.totalTrades},")
                    writer.write("${result.winningTrades},")
                    writer.write("${result.losingTrades},")
                    writer.write("${result.winRate},")
                    writer.write("${result.profitFactor},")
                    writer.write("${result.sharpeRatio},")
                    writer.write("${result.averageWin},")
                    writer.write("${result.averageLoss},")
                    writer.write("${result.largestWin},")
                    writer.write("${result.largestLoss},")
                    writer.write("${result.maxDrawdown},")
                    writer.write("${result.maxDrawdownPercent},")
                    writer.write("${result.averageTradeDuration}\n")
                }
            }
            log.info { "Results exported to $filename" }
        } catch (e: Exception) {
            log.error(e) { "Failed to export results to CSV" }
        }
    }
}
