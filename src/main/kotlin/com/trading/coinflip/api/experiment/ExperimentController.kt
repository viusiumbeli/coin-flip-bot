package com.trading.coinflip.api.experiment

import com.trading.coinflip.common.dto.BacktestRunDetailDto
import com.trading.coinflip.experiment.CreateExperimentRequest
import com.trading.coinflip.experiment.ExperimentDetailResponse
import com.trading.coinflip.experiment.ExperimentService
import com.trading.coinflip.experiment.PaginatedRunsDto
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
    fun createExperiment(
        @RequestBody request: CreateExperimentRequest,
    ): ResponseEntity<CreateExperimentResponse> =
        try {
            log.info { "Initiating async experiment for ${request.symbol} ${request.timeframe} with ${request.numBacktests} backtests" }
            val experiment = experimentService.initiateExperiment(request)
            ResponseEntity.status(HttpStatus.ACCEPTED).body(
                CreateExperimentResponse(
                    id = experiment.id!!,
                    status = experiment.status,
                    message = "Experiment started. Poll GET /api/experiments/${experiment.id}/status for progress.",
                ),
            )
        } catch (e: IllegalArgumentException) {
            log.error { "Invalid request: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error creating experiment" }
            ResponseEntity.internalServerError().build()
        }

    /**
     * Gets the current status of an experiment (for progress polling).
     */
    @GetMapping("/{id}/status")
    fun getExperimentStatus(
        @PathVariable id: Long,
    ): ResponseEntity<ExperimentStatusResponse> =
        try {
            val status = experimentService.getExperimentStatus(id)
            ResponseEntity.ok(status)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting experiment status $id" }
            ResponseEntity.internalServerError().build()
        }

    /**
     * Cancels a running experiment.
     */
    @PostMapping("/{id}/cancel")
    fun cancelExperiment(
        @PathVariable id: Long,
    ): ResponseEntity<ExperimentStatusResponse> =
        try {
            val status = experimentService.cancelExperiment(id)
            ResponseEntity.ok(status)
        } catch (e: IllegalArgumentException) {
            log.error { "Cannot cancel experiment $id: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error cancelling experiment $id" }
            ResponseEntity.internalServerError().build()
        }

    @GetMapping
    fun listExperiments(): ResponseEntity<List<ExperimentSummaryResponse>> =
        try {
            val experiments = experimentService.listExperiments()
            ResponseEntity.ok(experiments)
        } catch (e: Exception) {
            log.error(e) { "Error listing experiments" }
            ResponseEntity.internalServerError().build()
        }

    @GetMapping("/{id}")
    fun getExperiment(
        @PathVariable id: Long,
    ): ResponseEntity<ExperimentDetailResponse> =
        try {
            val experiment = experimentService.getExperiment(id)
            ResponseEntity.ok(experiment)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting experiment $id" }
            ResponseEntity.internalServerError().build()
        }

    @GetMapping("/{id}/runs")
    fun getExperimentRuns(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @RequestParam(defaultValue = "runNumber") sortBy: String,
        @RequestParam(defaultValue = "asc") sortDir: String,
    ): ResponseEntity<PaginatedRunsDto> =
        try {
            val runs = experimentService.getExperimentRuns(id, page, size, sortBy, sortDir)
            ResponseEntity.ok(runs)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting experiment runs for $id" }
            ResponseEntity.internalServerError().build()
        }

    @GetMapping("/runs/{runId}")
    fun getBacktestRun(
        @PathVariable runId: Long,
    ): ResponseEntity<BacktestRunDetailDto> =
        try {
            val run = experimentService.getBacktestRun(runId)
            ResponseEntity.ok(run)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting backtest run $runId" }
            ResponseEntity.internalServerError().build()
        }

    @PostMapping("/compare")
    fun compareExperiments(
        @RequestBody request: CompareExperimentsRequest,
    ): ResponseEntity<ExperimentComparisonResponse> {
        return try {
            if (request.experimentIds.size < 2) {
                return ResponseEntity.badRequest().build()
            }
            val comparison = experimentService.compareExperiments(request.experimentIds)
            ResponseEntity.ok(comparison)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error comparing experiments" }
            ResponseEntity.internalServerError().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteExperiment(
        @PathVariable id: Long,
    ): ResponseEntity<Void> =
        try {
            experimentService.deleteExperiment(id)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error deleting experiment $id" }
            ResponseEntity.internalServerError().build()
        }
}
