package com.trading.coinflip.live

import com.trading.coinflip.common.util.withReentrantLock
import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.model.TradingState
import kotlinx.coroutines.sync.Mutex

/**
 * Thread-safe state holder for a single live trading session.
 * Wraps TradingState and adds session-specific metadata.
 *
 * Uses reentrant mutex to allow nested lock acquisition from the same coroutine.
 */
class LiveTradingStateHolder(
    val sessionId: Long,
    val symbol: String,
    initialState: TradingState,
    var lastCandle: CandleEntity? = null,
) {
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
