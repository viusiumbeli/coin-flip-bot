package com.trading.coinflip.data

import com.trading.coinflip.common.model.ExperimentTradeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ExperimentTradeRepository : JpaRepository<ExperimentTradeEntity, Long> {
    fun findByBacktestRunIdOrderByTradeNumberAsc(backtestRunId: Long): List<ExperimentTradeEntity>
}
