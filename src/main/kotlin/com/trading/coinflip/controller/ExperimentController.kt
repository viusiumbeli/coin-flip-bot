package com.trading.coinflip.controller

import com.trading.coinflip.dto.*
import com.trading.coinflip.model.ExperimentStatus
import com.trading.coinflip.service.ExperimentService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/experiments")
@CrossOrigin(origins = ["*"])
class ExperimentController(
    private val experimentService: ExperimentService
) {

    /**
     * Creates a new experiment asynchronously.
     * Returns 202 Accepted immediately with experiment ID for status polling.
     */
    @PostMapping
    fun createExperiment(@RequestBody request: CreateExperimentRequest): ResponseEntity<CreateExperimentResponse> {
        return try {
            log.info { "Initiating async experiment for ${request.symbol} ${request.timeframe} with ${request.numBacktests} backtests" }
            val experiment = experimentService.initiateExperiment(request)
            ResponseEntity.status(HttpStatus.ACCEPTED).body(
                CreateExperimentResponse(
                    id = experiment.id!!,
                    status = experiment.status,
                    message = "Experiment started. Poll GET /api/experiments/${experiment.id}/status for progress."
                )
            )
        } catch (e: IllegalArgumentException) {
            log.error { "Invalid request: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error creating experiment" }
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Gets the current status of an experiment (for progress polling).
     */
    @GetMapping("/{id}/status")
    fun getExperimentStatus(@PathVariable id: Long): ResponseEntity<ExperimentStatusDto> {
        return try {
            val status = experimentService.getExperimentStatus(id)
            ResponseEntity.ok(status)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting experiment status $id" }
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Cancels a running experiment.
     */
    @PostMapping("/{id}/cancel")
    fun cancelExperiment(@PathVariable id: Long): ResponseEntity<ExperimentStatusDto> {
        return try {
            val status = experimentService.cancelExperiment(id)
            ResponseEntity.ok(status)
        } catch (e: IllegalArgumentException) {
            log.error { "Cannot cancel experiment $id: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error cancelling experiment $id" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping
    fun listExperiments(): ResponseEntity<List<ExperimentSummaryDto>> {
        return try {
            val experiments = experimentService.listExperiments()
            ResponseEntity.ok(experiments)
        } catch (e: Exception) {
            log.error(e) { "Error listing experiments" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/{id}")
    fun getExperiment(@PathVariable id: Long): ResponseEntity<ExperimentDetailDto> {
        return try {
            val experiment = experimentService.getExperiment(id)
            ResponseEntity.ok(experiment)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting experiment $id" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/{id}/runs")
    fun getExperimentRuns(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int
    ): ResponseEntity<PaginatedRunsDto> {
        return try {
            val runs = experimentService.getExperimentRuns(id, page, size)
            ResponseEntity.ok(runs)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting experiment runs for $id" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/runs/{runId}")
    fun getBacktestRun(@PathVariable runId: Long): ResponseEntity<BacktestRunDetailDto> {
        return try {
            val run = experimentService.getBacktestRun(runId)
            ResponseEntity.ok(run)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error getting backtest run $runId" }
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/compare")
    fun compareExperiments(@RequestBody request: CompareExperimentsRequest): ResponseEntity<ExperimentComparisonDto> {
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
    fun deleteExperiment(@PathVariable id: Long): ResponseEntity<Void> {
        return try {
            experimentService.deleteExperiment(id)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error(e) { "Error deleting experiment $id" }
            ResponseEntity.internalServerError().build()
        }
    }
}
