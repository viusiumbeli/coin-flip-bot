package com.trading.coinflip.api.simulation

import com.trading.coinflip.simulation.SimulationService
import com.trading.coinflip.simulation.SimulationStateDto
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = ["*"])
class SimulationController(
    private val simulationService: SimulationService,
) {
    private val log = KotlinLogging.logger {}

    @PostMapping("/init")
    fun initialize(
        @RequestBody request: SimulationInitRequest,
    ): ResponseEntity<SimulationStateDto> =
        try {
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

    @PostMapping("/next")
    fun advanceCandle(): ResponseEntity<SimulationStateDto> =
        try {
            val state = simulationService.advanceCandle()
            ResponseEntity.ok(state)
        } catch (e: IllegalStateException) {
            log.warn { "Cannot advance: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error advancing candle: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }

    @PostMapping("/previous")
    fun previousCandle(): ResponseEntity<SimulationStateDto> =
        try {
            val state = simulationService.previousCandle()
            ResponseEntity.ok(state)
        } catch (e: IllegalStateException) {
            log.warn { "Cannot go back: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error going to previous candle: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }

    @PostMapping("/reset")
    fun reset(): ResponseEntity<SimulationStateDto> =
        try {
            val state = simulationService.reset()
            ResponseEntity.ok(state)
        } catch (e: IllegalStateException) {
            log.warn { "Cannot reset: ${e.message}" }
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error(e) { "Error resetting simulation: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }

    @GetMapping("/state")
    fun getCurrentState(): ResponseEntity<SimulationStateDto> =
        try {
            val state = simulationService.getCurrentState()
            ResponseEntity.ok(state)
        } catch (e: Exception) {
            log.error(e) { "Error getting simulation state: ${e.message}" }
            ResponseEntity.internalServerError().build()
        }
}
