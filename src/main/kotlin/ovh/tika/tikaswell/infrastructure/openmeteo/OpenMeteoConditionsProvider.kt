package ovh.tika.tikaswell.infrastructure.openmeteo

import com.fasterxml.jackson.annotation.JsonProperty
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TimeWindow
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

@Component
class OpenMeteoConditionsProvider(
	private val properties: OpenMeteoProperties,
	restTemplateBuilder: RestTemplateBuilder,
	private val clock: Clock,
) : ovh.tika.tikaswell.domain.ConditionsProvider {
	override val name: String = "Open-Meteo"

	private val restTemplate: RestTemplate = restTemplateBuilder.build()

	override fun fetchCurrentConditions(spot: Spot): CurrentConditions {
		val now = clock.instant()
		val window = TimeWindow(now.minus(Duration.ofHours(6)), now.plus(Duration.ofHours(6)))
		val snapshot = fetchSnapshots(spot, window).minByOrNull { Duration.between(it.timestamp, now).abs() }
			?: error("Aucune condition Open-Meteo disponible pour ${spot.name}")

		return CurrentConditions(
			spot = spot,
			fetchedAt = now,
			snapshot = snapshot,
		)
	}

	override fun fetchHistoricalConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> =
		fetchSnapshots(spot, window)

	override fun fetchForecastConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> =
		fetchSnapshots(spot, window)

	private fun fetchSnapshots(spot: Spot, window: TimeWindow): List<ConditionSnapshot> {
		val weather = fetchWeather(spot, window)
		val marine = fetchMarine(spot, window)

		return weather.map { weatherPoint ->
			val marinePoint = marine.nearestTo(weatherPoint.timestamp)
			ConditionSnapshot(
				spotId = spot.id,
				timestamp = weatherPoint.timestamp,
				windSpeedKmh = weatherPoint.windSpeedKmh,
				windGustKmh = weatherPoint.windGustKmh,
				windDirection = weatherPoint.windDirection,
				waveHeightMeters = marinePoint?.waveHeightMeters,
				wavePeriodSeconds = marinePoint?.wavePeriodSeconds,
				waveDirection = marinePoint?.waveDirection,
				providerName = name,
			)
		}
	}

	private fun fetchWeather(spot: Spot, window: TimeWindow): List<WeatherPoint> {
		val uri = UriComponentsBuilder.fromUriString(properties.weatherBaseUrl)
			.path("/v1/forecast")
			.queryParam("latitude", spot.latitude)
			.queryParam("longitude", spot.longitude)
			.queryParam("hourly", "wind_speed_10m,wind_gusts_10m,wind_direction_10m")
			.queryParam("wind_speed_unit", "kmh")
			.queryParam("timezone", "UTC")
			.queryParam("start_date", window.startsAt.utcDate())
			.queryParam("end_date", window.endsAt.utcDate())
			.build()
			.toUri()

		val response = restTemplate.getForObject(uri, OpenMeteoHourlyResponse::class.java)
			?: return emptyList()

		return response.hourly.toWeatherPoints()
	}

	private fun fetchMarine(spot: Spot, window: TimeWindow): List<MarinePoint> {
		val uri = UriComponentsBuilder.fromUriString(properties.marineBaseUrl)
			.path("/v1/marine")
			.queryParam("latitude", spot.latitude)
			.queryParam("longitude", spot.longitude)
			.queryParam("hourly", "wave_height,wave_period,wave_direction")
			.queryParam("timezone", "UTC")
			.queryParam("start_date", window.startsAt.utcDate())
			.queryParam("end_date", window.endsAt.utcDate())
			.build()
			.toUri()

		val response = restTemplate.getForObject(uri, OpenMeteoHourlyResponse::class.java)
			?: return emptyList()

		return response.hourly.toMarinePoints()
	}

	private fun List<MarinePoint>.nearestTo(timestamp: Instant): MarinePoint? =
		// Les modèles météo et marine n'ont pas toujours le même pas horaire.
		// On rattache donc la mesure marine la plus proche si elle reste dans une fenêtre raisonnable.
		minByOrNull { Duration.between(it.timestamp, timestamp).abs() }
			?.takeIf { Duration.between(it.timestamp, timestamp).abs() <= Duration.ofHours(2) }
}

internal data class WeatherPoint(
	val timestamp: Instant,
	val windSpeedKmh: Double,
	val windGustKmh: Double?,
	val windDirection: Direction?,
)

internal data class MarinePoint(
	val timestamp: Instant,
	val waveHeightMeters: Double?,
	val wavePeriodSeconds: Double?,
	val waveDirection: Direction?,
)

internal data class OpenMeteoHourlyResponse(
	val hourly: OpenMeteoHourly = OpenMeteoHourly(),
)

internal data class OpenMeteoHourly(
	val time: List<LocalDateTime> = emptyList(),
	@JsonProperty("wind_speed_10m")
	val windSpeed10m: List<Double?> = emptyList(),
	@JsonProperty("wind_gusts_10m")
	val windGusts10m: List<Double?> = emptyList(),
	@JsonProperty("wind_direction_10m")
	val windDirection10m: List<Int?> = emptyList(),
	@JsonProperty("wave_height")
	val waveHeight: List<Double?> = emptyList(),
	@JsonProperty("wave_period")
	val wavePeriod: List<Double?> = emptyList(),
	@JsonProperty("wave_direction")
	val waveDirection: List<Int?> = emptyList(),
) {
	fun toWeatherPoints(): List<WeatherPoint> =
		time.mapIndexedNotNull { index, timestamp ->
			val windSpeed = windSpeed10m.valueAt(index) ?: return@mapIndexedNotNull null
			WeatherPoint(
				timestamp = timestamp.toInstant(ZoneOffset.UTC),
				windSpeedKmh = windSpeed,
				windGustKmh = windGusts10m.valueAt(index),
				windDirection = windDirection10m.valueAt(index)?.let(::Direction),
			)
		}

	fun toMarinePoints(): List<MarinePoint> =
		time.mapIndexed { index, timestamp ->
			MarinePoint(
				timestamp = timestamp.toInstant(ZoneOffset.UTC),
				waveHeightMeters = waveHeight.valueAt(index),
				wavePeriodSeconds = wavePeriod.valueAt(index),
				waveDirection = waveDirection.valueAt(index)?.let(::Direction),
			)
		}

	private fun <T> List<T>.valueAt(index: Int): T? =
		if (index in indices) this[index] else null
}

private fun Instant.utcDate(): LocalDate =
	atOffset(ZoneOffset.UTC).toLocalDate()
