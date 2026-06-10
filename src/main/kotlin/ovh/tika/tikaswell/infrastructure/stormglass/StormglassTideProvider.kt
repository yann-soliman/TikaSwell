package ovh.tika.tikaswell.infrastructure.stormglass

import com.fasterxml.jackson.annotation.JsonProperty
import ovh.tika.tikaswell.application.tide.TideProvider
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TideEventType
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Component
class StormglassTideProvider(
	private val properties: StormglassProperties,
	restTemplateBuilder: RestTemplateBuilder,
	private val clock: Clock,
) : TideProvider {
	override val name: String = "Stormglass"

	override val requiredCallsPerFetch: Int = 2

	private val restTemplate: RestTemplate = restTemplateBuilder.build()

	override fun unavailableReason(): TideUnavailableReason? =
		if (properties.hasApiKey) null else TideUnavailableReason.MISSING_API_KEY

	override fun unavailableMessage(): String? =
		if (properties.hasApiKey) null else "Clé API Stormglass absente"

	override fun fetchTideDay(spot: Spot, date: LocalDate): TideDayCache {
		val points = fetchSeaLevel(spot, date)
		val extremes = fetchExtremes(spot, date)
		val station = points.meta.station ?: extremes.meta.station

		return TideDayCache(
			id = null,
			spotId = spot.id,
			date = date,
			providerName = name,
			fetchedAt = clock.instant(),
			stationName = station?.name,
			stationDistanceKilometers = station?.distance,
			coefficient = null,
			unavailableReason = null,
			unavailableMessage = null,
			points = points.data.mapNotNull { point ->
				point.time?.let { timestamp ->
					TidePoint(timestamp = timestamp, waterHeightMeters = point.heightMeters)
				}
			},
			events = extremes.data.mapNotNull { event ->
				val timestamp = event.time ?: return@mapNotNull null
				val type = event.type.toTideEventType() ?: return@mapNotNull null
				TideEvent(type = type, timestamp = timestamp, waterHeightMeters = event.height)
			},
		)
	}

	private fun fetchSeaLevel(spot: Spot, date: LocalDate): StormglassSeaLevelResponse =
		exchange(spot, date, "/v2/tide/sea-level/point", StormglassSeaLevelResponse::class.java)

	private fun fetchExtremes(spot: Spot, date: LocalDate): StormglassExtremesResponse =
		exchange(spot, date, "/v2/tide/extremes/point", StormglassExtremesResponse::class.java)

	private fun <T> exchange(spot: Spot, date: LocalDate, path: String, responseType: Class<T>): T {
		val window = date.window()
		val uri = UriComponentsBuilder.fromUriString(properties.baseUrl)
			.path(path)
			.queryParam("lat", spot.latitude)
			.queryParam("lng", spot.longitude)
			.queryParam("start", window.startsAt.epochSecond)
			.queryParam("end", window.endsAt.epochSecond)
			.build()
			.toUri()

		val headers = HttpHeaders().apply {
			set("Authorization", properties.apiKey)
		}

		return restTemplate.exchange(uri, HttpMethod.GET, HttpEntity<Unit>(headers), responseType).body
			?: error("Réponse Stormglass vide")
	}

}

internal data class TideDayWindow(
	val startsAt: Instant,
	val endsAt: Instant,
)

internal data class StormglassSeaLevelResponse(
	val data: List<StormglassSeaLevelPoint> = emptyList(),
	val meta: StormglassMeta = StormglassMeta(),
)

internal data class StormglassSeaLevelPoint(
	val height: Double? = null,
	@JsonProperty("sg")
	val stormglassHeight: Double? = null,
	val time: Instant? = null,
) {
	val heightMeters: Double?
		get() = height ?: stormglassHeight
}

internal data class StormglassExtremesResponse(
	val data: List<StormglassExtremePoint> = emptyList(),
	val meta: StormglassMeta = StormglassMeta(),
)

internal data class StormglassExtremePoint(
	val height: Double? = null,
	val time: Instant? = null,
	val type: String? = null,
)

internal data class StormglassMeta(
	val station: StormglassStation? = null,
)

internal data class StormglassStation(
	val name: String? = null,
	@JsonProperty("distance")
	val distance: Double? = null,
)

private fun LocalDate.window(): TideDayWindow =
	TideDayWindow(
		startsAt = atStartOfDay().toInstant(ZoneOffset.UTC),
		endsAt = plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
	)

private fun String?.toTideEventType(): TideEventType? =
	when (this?.lowercase(Locale.ROOT)) {
		"high" -> TideEventType.HIGH
		"low" -> TideEventType.LOW
		else -> null
	}
