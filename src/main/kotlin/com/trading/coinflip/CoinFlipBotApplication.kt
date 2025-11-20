package com.trading.coinflip

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CoinFlipBotApplication

fun main(args: Array<String>) {
    runApplication<CoinFlipBotApplication>(*args)
}
