package com.offlineassistant.core.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CachingWeatherProviderTest {
    @Test
    fun storesFreshResultWithoutChangingItsSource() {
        val cache = MemoryWeatherCache()
        val fresh = result(source = "online")
        val provider = CachingWeatherProvider(WeatherProvider { fresh }, cache)

        val actual = provider.currentWeather("Москва")

        assertEquals("online", actual.source)
        assertEquals(fresh, cache.read("Москва"))
    }

    @Test
    fun returnsLastResultAsCacheWhenUpstreamFails() {
        val cache = MemoryWeatherCache().apply { write(result(source = "online")) }
        val provider = CachingWeatherProvider(WeatherProvider { error("offline") }, cache)

        val actual = provider.currentWeather("Москва")

        assertEquals("cache", actual.source)
        assertEquals("2026-07-13T12:00:00+03:00", actual.updatedAt)
    }

    @Test
    fun reportsOfflineStateWhenNoCacheExists() {
        val provider = CachingWeatherProvider(WeatherProvider { error("offline") }, MemoryWeatherCache())

        val error = assertThrows(WeatherUnavailableException::class.java) {
            provider.currentWeather("Москва")
        }

        assertEquals(
            "Свежую погоду офлайн узнать нельзя, а сохраненного прогноза нет.",
            error.message
        )
    }

    private fun result(source: String) = WeatherResult(
        location = "Москва",
        temperatureC = 21,
        condition = "Облачно",
        feelsLikeC = 20,
        humidityPercent = 64,
        windMps = 3,
        forecast = listOf(WeatherForecastPoint("12:00", 21, "cloudy")),
        source = source,
        updatedAt = "2026-07-13T12:00:00+03:00"
    )

    private class MemoryWeatherCache : WeatherCache {
        private val values = mutableMapOf<String, WeatherResult>()

        override fun read(location: String): WeatherResult? = values[location.lowercase()]

        override fun write(result: WeatherResult) {
            values[result.location.lowercase()] = result
        }
    }
}
