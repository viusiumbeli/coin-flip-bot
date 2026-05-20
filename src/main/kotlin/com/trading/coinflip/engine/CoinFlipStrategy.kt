package com.trading.coinflip.engine

import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.TrailingStopMode
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

    companion object {
        /**
         * Calculate trailing stop distance based on mode.
         * @param price Current price (for PERCENT mode)
         * @param atr ATR value (for ATR mode)
         * @param mode ATR or PERCENT
         * @param atrMultiplier Multiplier for ATR (default 3.0)
         * @param percentValue Percentage value (default 1.0)
         */
        fun calculateStopDistance(
            price: BigDecimal,
            atr: BigDecimal,
            mode: TrailingStopMode,
            atrMultiplier: BigDecimal,
            percentValue: BigDecimal,
        ): BigDecimal =
            when (mode) {
                TrailingStopMode.ATR -> atr * atrMultiplier
                TrailingStopMode.PERCENT -> price * percentValue / BigDecimal(100)
            }
    }

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
        trailingStopMode: TrailingStopMode,
        atrMultiplier: BigDecimal,
        trailingStopPercent: BigDecimal,
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

        // Calculate stop distance based on mode (ATR or PERCENT)
        val stopDistance = calculateStopDistance(entryPrice, atr, trailingStopMode, atrMultiplier, trailingStopPercent)

        // Calculate initial stop loss
        val initialStopLoss =
            when (side) {
                PositionSide.LONG -> entryPrice - stopDistance
                PositionSide.SHORT -> entryPrice + stopDistance
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
     *
     * Uses candle high/low for realistic stop checking:
     * - LONG: check if candle.low hit trailing stop, update trailing using candle.high
     * - SHORT: check if candle.high hit trailing stop, update trailing using candle.low
     *
     * IMPORTANT: Check stop hit FIRST using original trailing stop, then update.
     * We can't know intra-candle price sequence, so conservatively assume
     * unfavorable price movement happened before favorable movement.
     */
    fun updatePosition(
        position: PositionView,
        candle: CandleEntity,
        trailingStopMode: TrailingStopMode,
        atrMultiplier: BigDecimal,
        trailingStopPercent: BigDecimal,
    ): PositionUpdateResult {
        val atr =
            candle.atr
                ?: throw IllegalStateException("ATR not calculated for candle ${candle.symbol} at ${candle.openTime}")
        if (atr <= BigDecimal.ZERO) {
            return PositionUpdateResult.NoChange
        }

        // STEP 1: Check if ORIGINAL stop is hit using unfavorable price
        val unfavorablePrice =
            when (position.side) {
                PositionSide.LONG -> candle.low
                PositionSide.SHORT -> candle.high
            }

        val isStopHit =
            when (position.side) {
                PositionSide.LONG -> unfavorablePrice <= position.trailingStop
                PositionSide.SHORT -> unfavorablePrice >= position.trailingStop
            }

        if (isStopHit) {
            return PositionUpdateResult.StopHit(
                exitPrice = position.trailingStop, // Exit at ORIGINAL stop price
                exitTime = candle.openTime,
                exitReason = "Trailing stop hit",
                newTrailingStop = position.trailingStop,
                newHighestFavorablePrice = position.highestFavorablePrice,
            )
        }

        // STEP 2: Only update trailing stop if position survives this candle
        val favorablePrice =
            when (position.side) {
                PositionSide.LONG -> candle.high
                PositionSide.SHORT -> candle.low
            }

        val update =
            position.calculateTrailingStopUpdate(
                favorablePrice,
                atr,
                trailingStopMode,
                atrMultiplier,
                trailingStopPercent,
            )

        return if (update != null) {
            PositionUpdateResult.Updated(
                newTrailingStop = update.newTrailingStop,
                newHighestFavorablePrice = update.newHighestFavorablePrice,
            )
        } else {
            PositionUpdateResult.NoChange
        }
    }
}
