package com.trading.coinflip.common.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class Timeframe(
    val minutes: Int,
    @JsonValue val label: String,
) {
    ONE_MINUTE(1, "1m"),
    ONE_HOUR(60, "1h"),
    FOUR_HOURS(240, "4h"),
    ONE_DAY(1440, "1d"),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromLabel(label: String): Timeframe? = entries.find { it.label == label }
    }
}
