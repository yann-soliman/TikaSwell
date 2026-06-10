package ovh.tika.tikaswell.infrastructure.apimaree

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ApiMareeTideProviderTests {
	private lateinit var server: HttpServer
	private val requestedPaths = mutableListOf<String>()

	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)

	@BeforeEach
	fun startServer() {
		server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
			createContext("/") { exchange ->
				requestedPaths += exchange.requestURI.toString()
				when {
					exchange.requestURI.path.endsWith("/water-levels") -> exchange.respondJson(waterLevelsJson)
					else -> exchange.sendResponseHeaders(404, -1)
				}
			}
			start()
		}
	}

	@AfterEach
	fun stopServer() {
		server.stop(0)
	}

	@Test
	fun `provider maps api maree water levels response`() {
		val provider = provider(apiKey = "test-api-key")

		val tide = provider.fetchTideDay(spot, LocalDate.parse("2026-06-04"))

		assertThat(tide.providerName).isEqualTo("api-maree.fr")
		assertThat(tide.unavailableReason).isNull()
		assertThat(tide.stationName).isEqualTo("saint-nazaire")
		assertThat(tide.stationDistanceKilometers).isNull()
		assertThat(tide.points).hasSize(3)
		assertThat(tide.points[0].timestamp).isEqualTo(Instant.parse("2026-06-04T00:00:00Z"))
		assertThat(tide.points[0].waterHeightMeters).isEqualTo(4.71)
		assertThat(tide.points[2].waterHeightMeters).isEqualTo(4.81)
		assertThat(tide.events).isEmpty()
		assertThat(requestedPaths).hasSize(1)
		assertThat(requestedPaths.single()).contains("/water-levels")
		assertThat(requestedPaths.single()).contains("site=saint-nazaire")
		assertThat(requestedPaths.single()).contains("step=10")
		assertThat(requestedPaths.single()).contains("tz=UTC")
		assertThat(requestedPaths.single()).contains("key=test-api-key")
	}

	@Test
	fun `provider declares missing api key without exposing a secret`() {
		val provider = provider(apiKey = "")

		assertThat(provider.unavailableReason()).isEqualTo(TideUnavailableReason.MISSING_API_KEY)
		assertThat(provider.unavailableMessage()).isEqualTo("Clé API api-maree.fr absente")
	}

	private fun provider(apiKey: String): ApiMareeTideProvider =
		ApiMareeTideProvider(
			properties = ApiMareeProperties(
				baseUrl = "http://127.0.0.1:${server.address.port}",
				apiKey = apiKey,
				siteId = "saint-nazaire",
				stepMinutes = 10,
				timezone = "UTC",
			),
			restTemplateBuilder = RestTemplateBuilder(),
			clock = Clock.fixed(Instant.parse("2026-06-04T10:00:00Z"), ZoneOffset.UTC),
		)

	private fun HttpExchange.respondJson(body: String) {
		val bytes = body.toByteArray()
		responseHeaders.add("Content-Type", "application/json")
		sendResponseHeaders(200, bytes.size.toLong())
		responseBody.use { it.write(bytes) }
	}

	private companion object {
		val waterLevelsJson = """
			{
			  "site": "saint-nazaire",
			  "timezone": "UTC",
			  "from": "2026-06-04T00:00:00Z",
			  "to": "2026-06-05T00:00:00Z",
			  "step_minutes": 10,
			  "unit": "m",
			  "data": [
			    { "time": "2026-06-04T00:00:00Z", "height": 4.71 },
			    { "time": "2026-06-04T00:10:00Z", "height": 4.77 },
			    { "time": "2026-06-04T00:20:00Z", "height": 4.81 }
			  ]
			}
		""".trimIndent()
	}
}
