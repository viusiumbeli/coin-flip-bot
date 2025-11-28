package com.trading.coinflip.strategy

import com.trading.coinflip.model.Candle
import com.trading.coinflip.model.Position
import com.trading.coinflip.model.PositionSide
import com.trading.coinflip.model.PositionStatus
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

@Component
class CoinFlipStrategy(
    private val atrCalculator: ATRCalculator
) {

    private val log = KotlinLogging.logger {}

    private var positionIdCounter = 0L
    private val random = Random.Default

    /**
     * Flip a coin to decide position side
     * Returns true for LONG, false for SHORT
     */
    fun flipCoin(): Boolean = random.nextBoolean()

    /**
     * Calculate position size based on 1% risk model
     * Risk Amount = Account Balance * Risk Percentage
     * Position Size = Risk Amount / Stop Distance
     */
    fun calculatePositionSize(
        accountBalance: BigDecimal,
        riskPercent: BigDecimal,
        entryPrice: BigDecimal,
        stopLoss: BigDecimal
    ): BigDecimal {
        // Ensure proper scale for division to avoid precision loss
        val riskAmount = accountBalance * riskPercent.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
        val stopDistance = (entryPrice - stopLoss).abs()

        if (stopDistance <= BigDecimal.ZERO) {
            log.warn { "Invalid stop distance: $stopDistance" }
            return BigDecimal.ZERO
        }

        val positionSize = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP)

        return positionSize
    }

    /**
     * Create a new position with coin flip entry
     */
    fun createPosition(
        candle: Candle,
        candles: List<Candle>,
        candleIndex: Int,
        accountBalance: BigDecimal,
        riskPercent: BigDecimal,
        atrMultiplier: BigDecimal,
        balanceBeforeOpen: BigDecimal
    ): Position? {
        val atr = atrCalculator.getATRForCandle(candles, candleIndex)
        if (atr == null || atr <= BigDecimal.ZERO) {
            log.warn { "No valid ATR at index $candleIndex for ${candle.symbol}" }
            return null
        }

        val side = if (flipCoin()) PositionSide.LONG else PositionSide.SHORT
        val entryPrice = candle.close

        // Calculate initial stop loss based on ATR
        val initialStopLoss = when (side) {
            PositionSide.LONG -> entryPrice - (atr * atrMultiplier)
            PositionSide.SHORT -> entryPrice + (atr * atrMultiplier)
        }

        // Check minimum balance requirement before calculating position size
        val riskAmount = accountBalance * riskPercent.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
        val minRiskAmount = BigDecimal("0.01") // Minimum $0.01 risk to open position

        if (riskAmount < minRiskAmount) {
            log.debug {
                "Insufficient balance to open position. " +
                "Available: $accountBalance, Risk amount: $riskAmount, Minimum required: $minRiskAmount"
            }
            return null
        }

        // Calculate position size based on risk
        var positionSize = calculatePositionSize(
            accountBalance = accountBalance,
            riskPercent = riskPercent,
            entryPrice = entryPrice,
            stopLoss = initialStopLoss
        )

        if (positionSize <= BigDecimal.ZERO) {
            log.warn { "Invalid position size calculated: $positionSize" }
            return null
        }

        // For both LONG and SHORT positions, ensure we don't exceed available balance
        val positionValue = positionSize * entryPrice
        if (positionValue > accountBalance) {
            // Cap position size to what we can afford
            positionSize = accountBalance.divide(entryPrice, 8, RoundingMode.DOWN)
            log.debug {
                "Position size capped to $positionSize due to insufficient balance. " +
                "Required: $positionValue, Available: $accountBalance"
            }

            // After capping, check if position size is still valid
            if (positionSize <= BigDecimal.ZERO) {
                log.warn { "Position size is zero or negative after capping. Cannot open position." }
                return null
            }
        }

        // Calculate balance after opening position (allocating capital)
        // For both LONG and SHORT: lock up full position value
        val allocatedCapital = positionSize * entryPrice
        val balanceAfterOpen = balanceBeforeOpen - allocatedCapital

        val position = Position(
            id = ++positionIdCounter,
            symbol = candle.symbol,
            timeframe = candle.timeframe,
            side = side,
            entryTime = candle.openTime,
            entryPrice = entryPrice,
            positionSize = positionSize,
            initialStopLoss = initialStopLoss,
            trailingStop = initialStopLoss,
            highestFavorablePrice = entryPrice,
            status = PositionStatus.OPEN,
            balanceBeforeOpen = balanceBeforeOpen,
            balanceAfterOpen = balanceAfterOpen,
            allocatedCapital = allocatedCapital
        )

        log.debug {
            "Created ${side.name} position for ${candle.symbol} at $entryPrice, " +
                "stop: $initialStopLoss, size: $positionSize, ATR: $atr"
        }

        return position
    }

    /**
     * Update position trailing stop and check if position should be closed
     */
    fun updatePosition(
        position: Position,
        candle: Candle,
        candles: List<Candle>,
        candleIndex: Int,
        atrMultiplier: BigDecimal
    ): Boolean {
        val atr = atrCalculator.getATRForCandle(candles, candleIndex)
        if (atr == null || atr <= BigDecimal.ZERO) {
            return false
        }

        // Update trailing stop
        position.updateTrailingStop(candle.close, atr, atrMultiplier)

        // Check if stop is hit
        if (position.isStopHit(candle.close)) {
            position.exitPrice = position.trailingStop
            position.exitTime = candle.openTime
            position.exitReason = "Trailing stop hit"
            return true
        }

        return false
    }

    fun reset() {
        positionIdCounter = 0L
    }
}
