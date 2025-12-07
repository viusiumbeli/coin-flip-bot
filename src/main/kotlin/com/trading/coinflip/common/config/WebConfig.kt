package com.trading.coinflip.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

@Configuration
class WebConfig {
    private val svgMediaType = MediaType.parseMediaType("image/svg+xml")

    @Bean
    fun faviconRouter(): RouterFunction<ServerResponse> {
        val faviconSvg = ClassPathResource("static/favicon.svg")
        val appleTouchIcon = ClassPathResource("static/apple-touch-icon.png")
        val appleTouchIconPrecomposed = ClassPathResource("static/apple-touch-icon-precomposed.png")

        val faviconRoute =
            RouterFunctions
                .route()
                .GET("/favicon.ico") {
                    ServerResponse
                        .ok()
                        .contentType(svgMediaType)
                        .bodyValue(faviconSvg)
                }.build()

        val appleTouchRoute =
            RouterFunctions
                .route()
                .GET("/apple-touch-icon.png") {
                    ServerResponse
                        .ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .bodyValue(appleTouchIcon)
                }.build()

        val appleTouchPrecomposedRoute =
            RouterFunctions
                .route()
                .GET("/apple-touch-icon-precomposed.png") {
                    ServerResponse
                        .ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .bodyValue(appleTouchIconPrecomposed)
                }.build()

        return faviconRoute
            .and(appleTouchRoute)
            .and(appleTouchPrecomposedRoute)
    }
}
