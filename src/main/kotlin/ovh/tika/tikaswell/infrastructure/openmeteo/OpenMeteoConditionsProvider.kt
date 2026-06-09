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
				wavePeakPeriodSeconds = marinePoint?.wavePeakPeriodSeconds,
				waveDirection = marinePoint?.waveDirection,
				windWaveHeightMeters = marinePoint?.windWaveHeightMeters,
				windWavePeriodSeconds = marinePoint?.windWavePeriodSeconds,
				windWavePeakPeriodSeconds = marinePoint?.windWavePeakPeriodSeconds,
				windWaveDirection = marinePoint?.windWaveDirection,
				swellWaveHeightMeters = marinePoint?.swellWaveHeightMeters,
				swellWavePeriodSeconds = marinePoint?.swellWavePeriodSeconds,
				swellWavePeakPeriodSeconds = marinePoint?.swellWavePeakPeriodSeconds,
				swellWaveDirection = marinePoint?.swellWaveDirection,
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
			.queryParam("hourly", MARINE_HOURLY_VARIABLES)
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
	val wavePeakPeriodSeconds: Double?,
	val waveDirection: Direction?,
	val windWaveHeightMeters: Double?,
	val windWavePeriodSeconds: Double?,
	val windWavePeakPeriodSeconds: Double?,
	val windWaveDirection: Direction?,
	val swellWaveHeightMeters: Double?,
	val swellWavePeriodSeconds: Double?,
	val swellWavePeakPeriodSeconds: Double?,
	val swellWaveDirection: Direction?,
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
	@JsonProperty("wave_peak_period")
	val wavePeakPeriod: List<Double?> = emptyList(),
	@JsonProperty("wave_direction")
	val waveDirection: List<Int?> = emptyList(),
	@JsonProperty("wind_wave_height")
	val windWaveHeight: List<Double?> = emptyList(),
	@JsonProperty("wind_wave_period")
	val windWavePeriod: List<Double?> = emptyList(),
	@JsonProperty("wind_wave_peak_period")
	val windWavePeakPeriod: List<Double?> = emptyList(),
	@JsonProperty("wind_wave_direction")
	val windWaveDirection: List<Int?> = emptyList(),
	@JsonProperty("swell_wave_height")
	val swellWaveHeight: List<Double?> = emptyList(),
	@JsonProperty("swell_wave_period")
	val swellWavePeriod: List<Double?> = emptyList(),
	@JsonProperty("swell_wave_peak_period")
	val swellWavePeakPeriod: List<Double?> = emptyList(),
	@JsonProperty("swell_wave_direction")
	val swellWaveDirection: List<Int?> = emptyList(),
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
				wavePeakPeriodSeconds = wavePeakPeriod.valueAt(index),
				waveDirection = waveDirection.valueAt(index)?.let(::Direction),
				windWaveHeightMeters = windWaveHeight.valueAt(index),
				windWavePeriodSeconds = windWavePeriod.valueAt(index),
				windWavePeakPeriodSeconds = windWavePeakPeriod.valueAt(index),
				windWaveDirection = windWaveDirection.valueAt(index)?.let(::Direction),
				swellWaveHeightMeters = swellWaveHeight.valueAt(index),
				swellWavePeriodSeconds = swellWavePeriod.valueAt(index),
				swellWavePeakPeriodSeconds = swellWavePeakPeriod.valueAt(index),
				swellWaveDirection = swellWaveDirection.valueAt(index)?.let(::Direction),
			)
		}

	private fun <T> List<T>.valueAt(index: Int): T? =
		if (index in indices) this[index] else null
}

private fun Instant.utcDate(): LocalDate =
	atOffset(ZoneOffset.UTC).toLocalDate()

private const val MARINE_HOURLY_VARIABLES =
	"wave_height,wave_period,wave_peak_period,wave_direction," +
		"wind_wave_height,wind_wave_period,wind_wave_peak_period,wind_wave_direction," +
		"swell_wave_height,swell_wave_period,swell_wave_peak_period,swell_wave_direction"
