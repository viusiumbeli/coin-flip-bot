package com.trading.coinflip.common.model

enum class Timeframe(
    val minutes: Int,
    val label: String,
) {
    ONE_HOUR(60, "1h"),
    FOUR_HOURS(240, "4h"),
    ONE_DAY(1440, "1d"),
    ;

    companion object {
        fun fromLabel(label: String): Timeframe? = entries.find { it.label == label }
    }
}
