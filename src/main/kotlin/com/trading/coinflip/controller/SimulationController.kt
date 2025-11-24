package com.trading.coinflip.controller

import com.trading.coinflip.dto.SimulationInitRequest
import com.trading.coinflip.dto.SimulationStateDto
import com.trading.coinflip.simulation.SimulationService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = ["*"])
class SimulationController(
    private val simulationService: SimulationService
) {

    @PostMapping("/init")
    fun initialize(@RequestBody request: SimulationInitRequest): ResponseEntity<SimulationStateDto> {
        return try {
            log.info { "Initializing simulation for ${request.symbol} ${request.timeframe}" }
            val state = simulationService.initialize(request)
            ResponseEntity.ok(state)
        } catch (e: IllegalArgumentException) {
            log.error(e) { "Invalid request: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error initializing simulation: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/next")
    fun advanceCandle(): ResponseEntity<SimulationStateDto> {
        return try {
            val state = simulationService.advanceCandle()
            ResponseEntity.ok(state)
        } catch (e: IllegalStateException) {
            log.warn { "Cannot advance: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error advancing candle: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/previous")
    fun previousCandle(): ResponseEntity<SimulationStateDto> {
        return try {
            val state = simulationService.previousCandle()
            ResponseEntity.ok(state)
        } catch (e: IllegalStateException) {
            log.warn { "Cannot go back: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error going to previous candle: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/reset")
    fun reset(): ResponseEntity<SimulationStateDto> {
        return try {
            val state = simulationService.reset()
            ResponseEntity.ok(state)
        } catch (e: IllegalStateException) {
            log.warn { "Cannot reset: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error resetting simulation: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/state")
    fun getCurrentState(): ResponseEntity<SimulationStateDto> {
        return try {
            val state = simulationService.getCurrentState()
            ResponseEntity.ok(state)
        } catch (e: Exception) {
            log.error(e) { "Error getting simulation state: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }
    }
}
