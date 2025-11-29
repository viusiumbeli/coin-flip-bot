package com.trading.coinflip.api.simulation

import com.trading.coinflip.simulation.SimulationService
import com.trading.coinflip.simulation.SimulationStateDto
import mu.KotlinLogging
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
    ): SimulationStateDto {
        log.info { "Initializing simulation for ${request.symbol} ${request.timeframe}" }
        return simulationService.initialize(request)
    }

    @PostMapping("/next")
    fun advanceCandle(): SimulationStateDto = simulationService.advanceCandle()

    @PostMapping("/previous")
    fun previousCandle(): SimulationStateDto = simulationService.previousCandle()

    @PostMapping("/reset")
    fun reset(): SimulationStateDto = simulationService.reset()

    @GetMapping("/state")
    fun getCurrentState(): SimulationStateDto = simulationService.getCurrentState()
}
