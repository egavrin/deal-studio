package com.offlineassistant.core.weather

import java.time.OffsetDateTime

data class WeatherForecastPoint(
    val time: String,
    val temperatureC: Int,
    val condition: String,
)

data class WeatherResult(
    val location: String,
    val temperatureC: Int,
    val condition: String,
    val feelsLikeC: Int,
    val humidityPercent: Int,
    val windMps: Int,
    val forecast: List<WeatherForecastPoint>,
    val source: String,
    val updatedAt: String,
)

fun interface WeatherProvider {
    fun currentWeather(location: String): WeatherResult
}

interface WeatherCache {
    fun read(location: String): WeatherResult?

    fun write(result: WeatherResult)
}

object NoOpWeatherCache : WeatherCache {
    override fun read(location: String): WeatherResult? = null
    override fun write(result: WeatherResult) = Unit
}

class CachingWeatherProvider(
    private val upstream: WeatherProvider,
    private val cache: WeatherCache,
) : WeatherProvider {
    override fun currentWeather(location: String): WeatherResult {
        return runCatching { upstream.currentWeather(location) }
            .onSuccess(cache::write)
            .getOrElse { error ->
                cache.read(location)?.copy(source = "cache")
                    ?: throw WeatherUnavailableException(
                        "Свежую погоду офлайн узнать нельзя, а сохраненного прогноза нет.",
                        error,
                    )
            }
    }
}

class WeatherUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class MockWeatherProvider(
    private val clock: () -> OffsetDateTime,
) : WeatherProvider {
    override fun currentWeather(location: String): WeatherResult =
        WeatherResult(
            location = location,
            temperatureC = 21,
            condition = "Облачно",
            feelsLikeC = 20,
            humidityPercent = 64,
            windMps = 3,
            forecast = listOf(
                WeatherForecastPoint("12:00", 21, "cloudy"),
                WeatherForecastPoint("15:00", 23, "partly_cloudy"),
                WeatherForecastPoint("18:00", 20, "rain"),
            ),
            source = "mock",
            updatedAt = clock().toString(),
        )
}
