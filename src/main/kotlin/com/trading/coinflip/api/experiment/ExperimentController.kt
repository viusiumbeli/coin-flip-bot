package com.trading.coinflip.api.experiment

import com.trading.coinflip.api.exception.BadRequestException
import com.trading.coinflip.backtest.model.BacktestRunDetailDto
import com.trading.coinflip.experiment.CreateExperimentRequest
import com.trading.coinflip.experiment.ExperimentDetailResponse
import com.trading.coinflip.experiment.ExperimentService
import com.trading.coinflip.experiment.PaginatedRunsDto
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/experiments")
@CrossOrigin(origins = ["*"])
class ExperimentController(
    private val experimentService: ExperimentService,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Creates a new experiment asynchronously.
     * Returns 202 Accepted immediately with experiment ID for status polling.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    suspend fun createExperiment(
        @RequestBody request: CreateExperimentRequest,
    ): CreateExperimentResponse {
        log.info { "Initiating async experiment for ${request.symbol} ${request.timeframe} with ${request.numBacktests} backtests" }
        val experiment = experimentService.initiateExperiment(request)
        return CreateExperimentResponse(
            id = experiment.id!!,
            status = experiment.status,
            message = "Experiment started. Poll GET /api/experiments/${experiment.id}/status for progress.",
        )
    }

    /**
     * Gets the current status of an experiment (for progress polling).
     */
    @GetMapping("/{id}/status")
    suspend fun getExperimentStatus(
        @PathVariable id: Long,
    ): ExperimentStatusResponse = experimentService.getExperimentStatus(id)

    /**
     * Cancels a running experiment.
     */
    @PostMapping("/{id}/cancel")
    suspend fun cancelExperiment(
        @PathVariable id: Long,
    ): ExperimentStatusResponse = experimentService.cancelExperiment(id)

    @GetMapping
    suspend fun listExperiments(): List<ExperimentSummaryResponse> = experimentService.listExperiments()

    @GetMapping("/{id}")
    suspend fun getExperiment(
        @PathVariable id: Long,
    ): ExperimentDetailResponse = experimentService.getExperiment(id)

    @GetMapping("/{id}/runs")
    suspend fun getExperimentRuns(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @RequestParam(defaultValue = "runNumber") sortBy: String,
        @RequestParam(defaultValue = "asc") sortDir: String,
    ): PaginatedRunsDto = experimentService.getExperimentRuns(id, page, size)

    @GetMapping("/runs/{runId}")
    suspend fun getBacktestRun(
        @PathVariable runId: Long,
    ): BacktestRunDetailDto = experimentService.getBacktestRun(runId)

    @PostMapping("/compare")
    suspend fun compareExperiments(
        @RequestBody request: CompareExperimentsRequest,
    ): ExperimentComparisonResponse {
        if (request.experimentIds.size < 2) {
            throw BadRequestException("At least 2 experiments required for comparison")
        }
        return experimentService.compareExperiments(request.experimentIds)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteExperiment(
        @PathVariable id: Long,
    ) {
        experimentService.deleteExperiment(id)
    }
}
