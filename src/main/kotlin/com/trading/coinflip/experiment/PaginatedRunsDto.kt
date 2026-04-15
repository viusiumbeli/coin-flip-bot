package com.trading.coinflip.experiment

data class PaginatedRunsDto(
    val runs: List<BacktestRunSummaryDto>,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val totalElements: Long,
)
