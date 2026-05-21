package com.trading.coinflip.live

import com.trading.coinflip.candle.CandleEntity
import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.common.model.TrailingStopMode
import com.trading.coinflip.common.util.withReentrantLock
import com.trading.coinflip.engine.model.TradingState
import com.trading.coinflip.exchange.Exchange
import kotlinx.coroutines.sync.Mutex
import java.math.BigDecimal

/**
 * Thread-safe state holder for a single live trading session.
 * Wraps TradingState and adds session-specific metadata.
 *
 * Uses reentrant mutex to allow nested lock acquisition from the same coroutine.
 */
class LiveTradingStateHolder(
    val sessionId: Long,
    val symbol: String,
    val timeframe: Timeframe,
    val exchange: Exchange,
    val trailingStopMode: TrailingStopMode,
    val trailingStopPercent: BigDecimal,
    val atrMultiplier: BigDecimal,
    val leverage: Int,
    initialState: TradingState,
    var lastCandle: CandleEntity? = null,
) {
    val logPrefix: String get() = "[#$sessionId $symbol/${timeframe.label} $exchange]"

    private val mutex = Mutex()
    private var _state: TradingState = initialState

    val state: TradingState get() = _state

    suspend fun updateState(newState: TradingState): TradingState =
        mutex.withReentrantLock {
            _state = newState
            _state
        }

    suspend fun updateLastCandle(candle: CandleEntity) =
        mutex.withReentrantLock {
            lastCandle = candle
        }

    suspend fun <T> withState(block: suspend (TradingState) -> T): T =
        mutex.withReentrantLock {
            block(_state)
        }
}
