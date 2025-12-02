package com.trading.coinflip.live

import com.trading.coinflip.data.CandleEntity
import com.trading.coinflip.engine.model.TradingState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant

/**
 * Thread-safe state holder for a single live trading session.
 * Wraps TradingState and adds session-specific metadata.
 */
class LiveTradingStateHolder(
    val sessionId: Long,
    val symbol: String,
    initialState: TradingState,
    var lastAtr: BigDecimal? = null,
    var lastCandleClose: BigDecimal? = null,
    var lastCandleTime: Instant? = null,
) {
    private val mutex = Mutex()
    private var _state: TradingState = initialState

    val state: TradingState get() = _state

    suspend fun updateState(newState: TradingState): TradingState =
        mutex.withLock {
            _state = newState
            _state
        }

    suspend fun updateLastCandle(candle: CandleEntity) =
        mutex.withLock {
            lastAtr = candle.atr
            lastCandleClose = candle.close
            lastCandleTime = candle.openTime
        }

    suspend fun <T> withState(block: suspend (TradingState) -> T): T =
        mutex.withLock {
            block(_state)
        }
}
