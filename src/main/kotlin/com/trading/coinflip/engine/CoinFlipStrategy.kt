package com.trading.coinflip.engine

import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.model.Position
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.engine.model.PositionUpdateResult
import com.trading.coinflip.engine.model.PositionView
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

@Component
class CoinFlipStrategy {
    private val log = KotlinLogging.logger {}
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
        stopLoss: BigDecimal,
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
        candle: CandleEntity,
        accountBalance: BigDecimal,
        riskPercent: BigDecimal,
        atrMultiplier: BigDecimal,
        maxPositionSizeRate: BigDecimal,
        balanceBeforeOpen: BigDecimal,
        positionId: Long,
        maxAllocation: BigDecimal,
    ): Position? {
        val atr =
            candle.atr
                ?: throw IllegalStateException("ATR not calculated for candle ${candle.symbol} at ${candle.openTime}")
        if (atr <= BigDecimal.ZERO) {
            log.warn { "Invalid ATR value $atr for ${candle.symbol}" }
            return null
        }

        val side = if (flipCoin()) PositionSide.LONG else PositionSide.SHORT
        val entryPrice = candle.close

        // Calculate initial stop loss based on ATR
        val initialStopLoss =
            when (side) {
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
        var positionSize =
            calculatePositionSize(
                accountBalance = accountBalance,
                riskPercent = riskPercent,
                entryPrice = entryPrice,
                stopLoss = initialStopLoss,
            )

        if (positionSize <= BigDecimal.ZERO) {
            log.warn { "Invalid position size calculated: $positionSize" }
            return null
        }

        // Cap position to maxPositionSizePercent of balance
        val maxAllowedValue = accountBalance * maxPositionSizeRate
        var positionValue = positionSize * entryPrice
        if (positionValue > maxAllowedValue) {
            positionSize = maxAllowedValue.divide(entryPrice, 8, RoundingMode.DOWN)
            positionValue = positionSize * entryPrice
            log.debug {
                "Position capped to max size: $positionSize (${maxPositionSizeRate * BigDecimal(100)}% of balance)"
            }
        }

        // Cap to available balance (never allocate more than we have)
        if (positionValue > maxAllocation) {
            positionSize = maxAllocation.divide(entryPrice, 8, RoundingMode.DOWN)
            positionValue = positionSize * entryPrice
            log.debug { "Position capped to available: $positionSize (max allocation: $maxAllocation)" }
        }

        // For both LONG and SHORT positions, ensure we don't exceed available balance
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

        val position =
            Position(
                id = positionId,
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
                allocatedCapital = allocatedCapital,
            )

        log.debug {
            "Created ${side.name} position for ${candle.symbol} at $entryPrice, " +
                "stop: $initialStopLoss, size: $positionSize, ATR: $atr"
        }

        return position
    }

    /**
     * Evaluate position against current candle and return the result.
     * Does not mutate position - returns what changes should be made.
     */
    fun updatePosition(
        position: PositionView,
        candle: CandleEntity,
        atrMultiplier: BigDecimal,
    ): PositionUpdateResult {
        val atr =
            candle.atr
                ?: throw IllegalStateException("ATR not calculated for candle ${candle.symbol} at ${candle.openTime}")
        if (atr <= BigDecimal.ZERO) {
            return PositionUpdateResult.NoChange
        }

        // Calculate potential trailing stop update
        val update = position.calculateTrailingStopUpdate(candle.close, atr, atrMultiplier)

        // Determine current trailing stop and highest price (may be updated)
        val currentTrailingStop = update?.newTrailingStop ?: position.trailingStop
        val currentHighestPrice = update?.newHighestFavorablePrice ?: position.highestFavorablePrice

        // Check if stop is hit using potentially updated trailing stop
        val isStopHit =
            when (position.side) {
                PositionSide.LONG -> candle.close <= currentTrailingStop
                PositionSide.SHORT -> candle.close >= currentTrailingStop
            }

        return if (isStopHit) {
            PositionUpdateResult.StopHit(
                exitPrice = currentTrailingStop,
                exitTime = candle.openTime,
                exitReason = "Trailing stop hit",
                newTrailingStop = currentTrailingStop,
                newHighestFavorablePrice = currentHighestPrice,
            )
        } else if (update != null) {
            PositionUpdateResult.Updated(
                newTrailingStop = update.newTrailingStop,
                newHighestFavorablePrice = update.newHighestFavorablePrice,
            )
        } else {
            PositionUpdateResult.NoChange
        }
    }
}
