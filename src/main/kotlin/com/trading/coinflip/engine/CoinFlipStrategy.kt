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
     * Calculate position size based on 1% risk model INCLUDING transaction costs.
     * Risk Amount = Account Balance * Risk Percentage
     * Position Size = Risk Amount / (Stop Distance + Fee Per Unit)
     *
     * This ensures total risk (stop loss + fees) never exceeds the risk budget.
     */
    fun calculatePositionSize(
        accountBalance: BigDecimal,
        riskPercent: BigDecimal,
        entryPrice: BigDecimal,
        stopLoss: BigDecimal,
        feeRate: BigDecimal,
    ): BigDecimal {
        val riskAmount = accountBalance * riskPercent.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
        val stopDistance = (entryPrice - stopLoss).abs()

        // Include fee cost per unit in risk calculation
        val feePerUnit = entryPrice * feeRate.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
        val totalRiskPerUnit = stopDistance + feePerUnit

        if (totalRiskPerUnit <= BigDecimal.ZERO) {
            log.warn { "Invalid total risk per unit: $totalRiskPerUnit" }
            return BigDecimal.ZERO
        }

        return riskAmount.divide(totalRiskPerUnit, 8, RoundingMode.HALF_UP)
    }

    /**
     * Create a new position with coin flip entry
     * @param leverage Leverage multiplier (1 = no leverage, 10 = 10x)
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
        leverage: Int = 1,
        feeRate: BigDecimal,
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
                feeRate = feeRate,
            )

        if (positionSize <= BigDecimal.ZERO) {
            log.warn { "Invalid position size calculated: $positionSize" }
            return null
        }

        // With leverage, max position value = maxMargin * leverage
        // where maxMargin = accountBalance * maxPositionSizeRate
        val leverageBD = BigDecimal(leverage)
        val maxMargin = accountBalance * maxPositionSizeRate
        val maxAllowedValue = maxMargin * leverageBD

        var positionValue = positionSize * entryPrice
        if (positionValue > maxAllowedValue) {
            positionSize = maxAllowedValue.divide(entryPrice, 8, RoundingMode.DOWN)
            positionValue = positionSize * entryPrice
            log.debug {
                "Position capped to max size: $positionSize (${maxPositionSizeRate * BigDecimal(100)}% margin × ${leverage}x leverage)"
            }
        }

        // Cap to available margin (never allocate more margin than we have)
        // With leverage: margin required = positionValue / leverage
        val maxPositionFromAllocation = maxAllocation * leverageBD
        if (positionValue > maxPositionFromAllocation) {
            positionSize = maxPositionFromAllocation.divide(entryPrice, 8, RoundingMode.DOWN)
            positionValue = positionSize * entryPrice
            log.debug { "Position capped to available margin: $positionSize (max allocation: $maxAllocation × ${leverage}x)" }
        }

        // Ensure margin requirement doesn't exceed available balance
        var marginRequired = positionValue.divide(leverageBD, 8, RoundingMode.HALF_UP)
        if (marginRequired > accountBalance) {
            // Cap position size to what we can afford with leverage
            val maxAffordableValue = accountBalance * leverageBD
            positionSize = maxAffordableValue.divide(entryPrice, 8, RoundingMode.DOWN)
            positionValue = positionSize * entryPrice
            marginRequired = positionValue.divide(leverageBD, 8, RoundingMode.HALF_UP)
            log.debug {
                "Position size capped to $positionSize due to insufficient margin. " +
                    "Margin required: $marginRequired, Available: $accountBalance"
            }

            // After capping, check if position size is still valid
            if (positionSize <= BigDecimal.ZERO) {
                log.warn { "Position size is zero or negative after capping. Cannot open position." }
                return null
            }
        }

        // Allocated capital is the margin (not full position value)
        val allocatedCapital = marginRequired
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
                "stop: $initialStopLoss, size: $positionSize, margin: $allocatedCapital, leverage: ${leverage}x, ATR: $atr"
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
