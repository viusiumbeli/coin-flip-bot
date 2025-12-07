package com.trading.coinflip.common.config

import com.trading.coinflip.common.model.Timeframe
import com.trading.coinflip.engine.model.PositionSide
import com.trading.coinflip.engine.model.PositionStatus
import com.trading.coinflip.exchange.Exchange
import com.trading.coinflip.experiment.model.ExperimentStatus
import com.trading.coinflip.live.model.LiveSessionStatus
import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@Configuration
@EnableR2dbcRepositories(basePackages = ["com.trading.coinflip"])
class R2dbcConfig {
    @Bean
    fun r2dbcCustomConversions(connectionFactory: ConnectionFactory): R2dbcCustomConversions {
        val dialect = DialectResolver.getDialect(connectionFactory)
        val converters =
            listOf(
                TimeframeWriteConverter(),
                TimeframeReadConverter(),
                ExperimentStatusWriteConverter(),
                ExperimentStatusReadConverter(),
                PositionSideWriteConverter(),
                PositionSideReadConverter(),
                PositionStatusWriteConverter(),
                PositionStatusReadConverter(),
                LiveSessionStatusWriteConverter(),
                LiveSessionStatusReadConverter(),
                ExchangeWriteConverter(),
                ExchangeReadConverter(),
            )
        return R2dbcCustomConversions.of(dialect, converters)
    }
}

@WritingConverter
class TimeframeWriteConverter : Converter<Timeframe, String> {
    override fun convert(source: Timeframe): String = source.name
}

@ReadingConverter
class TimeframeReadConverter : Converter<String, Timeframe> {
    override fun convert(source: String): Timeframe = Timeframe.valueOf(source)
}

@WritingConverter
class ExperimentStatusWriteConverter : Converter<ExperimentStatus, String> {
    override fun convert(source: ExperimentStatus): String = source.name
}

@ReadingConverter
class ExperimentStatusReadConverter : Converter<String, ExperimentStatus> {
    override fun convert(source: String): ExperimentStatus = ExperimentStatus.valueOf(source)
}

@WritingConverter
class PositionSideWriteConverter : Converter<PositionSide, String> {
    override fun convert(source: PositionSide): String = source.name
}

@ReadingConverter
class PositionSideReadConverter : Converter<String, PositionSide> {
    override fun convert(source: String): PositionSide = PositionSide.valueOf(source)
}

@WritingConverter
class PositionStatusWriteConverter : Converter<PositionStatus, String> {
    override fun convert(source: PositionStatus): String = source.name
}

@ReadingConverter
class PositionStatusReadConverter : Converter<String, PositionStatus> {
    override fun convert(source: String): PositionStatus = PositionStatus.valueOf(source)
}

@WritingConverter
class LiveSessionStatusWriteConverter : Converter<LiveSessionStatus, String> {
    override fun convert(source: LiveSessionStatus): String = source.name
}

@ReadingConverter
class LiveSessionStatusReadConverter : Converter<String, LiveSessionStatus> {
    override fun convert(source: String): LiveSessionStatus = LiveSessionStatus.valueOf(source)
}

@WritingConverter
class ExchangeWriteConverter : Converter<Exchange, String> {
    override fun convert(source: Exchange): String = source.name
}

@ReadingConverter
class ExchangeReadConverter : Converter<String, Exchange> {
    override fun convert(source: String): Exchange = Exchange.valueOf(source)
}
