package com.trading.coinflip.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper {
        val mapper = builder.build<ObjectMapper>()
        val module = SimpleModule()
        module.addSerializer(Instant::class.java, InstantSerializer())
        mapper.registerModule(module)
        return mapper
    }

    class InstantSerializer : JsonSerializer<Instant>() {
        private val formatter =
            DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)

        override fun serialize(
            value: Instant?,
            gen: JsonGenerator?,
            serializers: SerializerProvider?,
        ) {
            if (value != null && gen != null) {
                gen.writeString(formatter.format(value))
            }
        }
    }
}
