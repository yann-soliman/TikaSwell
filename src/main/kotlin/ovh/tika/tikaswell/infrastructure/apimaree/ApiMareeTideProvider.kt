package ovh.tika.tikaswell.infrastructure.apimaree

import ovh.tika.tikaswell.application.tide.TideProvider
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Component
class ApiMareeTideProvider(
	private val properties: ApiMareeProperties,
	restTemplateBuilder: RestTemplateBuilder,
	private val clock: Clock,
) : TideProvider {
	override val name: String = "api-maree.fr"

	override val requiredCallsPerFetch: Int = 1

	private val restTemplate: RestTemplate = restTemplateBuilder.build()

	override fun unavailableReason(): TideUnavailableReason? =
		if (properties.hasApiKey) null else TideUnavailableReason.MISSING_API_KEY

	override fun unavailableMessage(): String? =
		if (properties.hasApiKey) null else "Clé API api-maree.fr absente"

	override fun fetchTideDay(spot: Spot, date: LocalDate): TideDayCache {
		val response = exchange(date)

		return TideDayCache(
			id = null,
			spotId = spot.id,
			date = date,
			providerName = name,
			fetchedAt = clock.instant(),
			stationName = response.site,
			stationDistanceKilometers = null,
			coefficient = null,
			unavailableReason = null,
			unavailableMessage = null,
			points = response.data.mapNotNull { point ->
				point.time?.let { timestamp ->
					TidePoint(timestamp = timestamp, waterHeightMeters = point.height)
				}
			},
			events = emptyList(),
		)
	}

	private fun exchange(date: LocalDate): ApiMareeWaterLevelsResponse {
		val zoneId = ZoneId.of(properties.timezone)
		val from = date.atStartOfDay().atZone(zoneId)
		val to = date.plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zoneId)
		val uri = UriComponentsBuilder.fromUriString(properties.baseUrl)
			.path("/water-levels")
			.queryParam("site", properties.siteId)
			.queryParam("from", from.toLocalDateTime())
			.queryParam("to", to.toLocalDateTime())
			.queryParam("step", properties.stepMinutes)
			.queryParam("tz", properties.timezone)
			.queryParam("key", properties.apiKey)
			.build()
			.toUri()

		return restTemplate.getForObject(uri, ApiMareeWaterLevelsResponse::class.java)
			?: error("Réponse api-maree.fr vide")
	}
}

internal data class ApiMareeWaterLevelsResponse(
	val site: String? = null,
	val timezone: String? = null,
	val from: Instant? = null,
	val to: Instant? = null,
	val stepMinutes: Int? = null,
	val unit: String? = null,
	val data: List<ApiMareeWaterLevelPoint> = emptyList(),
)

internal data class ApiMareeWaterLevelPoint(
	val time: Instant? = null,
	val height: Double? = null,
)
