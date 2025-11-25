package com.trading.coinflip.controller

import com.trading.coinflip.dto.*
import com.trading.coinflip.service.ExperimentService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/experiments")
@CrossOrigin(origins = ["*"])
class ExperimentController(
    private val experimentService: ExperimentService
) {

    @PostMapping
    fun createExperiment(@RequestBody request: CreateExperimentRequest): ResponseEntity<ExperimentDetailDto> {
        return try {
            log.info { "Creating experiment for ${request.symbol} ${request.timeframe} with ${request.numBacktests} backtests" }
            val result = experimentService.createExperiment(request)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            log.error { "Invalid request: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error creating experiment" }
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
