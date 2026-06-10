package ovh.tika.tikaswell.infrastructure.stormglass

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideEventType
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

class StormglassTideProviderTests {
	private lateinit var server: HttpServer
	private val requestedPaths = mutableListOf<String>()
	private val authorizationHeaders = mutableListOf<String?>()

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
				authorizationHeaders += exchange.requestHeaders.getFirst("Authorization")
				when {
					exchange.requestURI.path.endsWith("/sea-level/point") -> exchange.respondJson(seaLevelJson)
					exchange.requestURI.path.endsWith("/extremes/point") -> exchange.respondJson(extremesJson)
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
	fun `provider maps Stormglass tide level and extremes responses`() {
		val provider = provider(apiKey = "test-api-key")

		val tide = provider.fetchTideDay(spot, LocalDate.parse("2026-06-04"))

		assertThat(tide.unavailableReason).isNull()
		assertThat(tide.stationName).isEqualTo("Saint-Nazaire")
		assertThat(tide.stationDistanceKilometers).isEqualTo(12.4)
		assertThat(tide.points).hasSize(2)
		assertThat(tide.points[0].waterHeightMeters).isEqualTo(3.2)
		assertThat(tide.events).hasSize(2)
		assertThat(tide.events[0].type).isEqualTo(TideEventType.LOW)
		assertThat(tide.events[1].type).isEqualTo(TideEventType.HIGH)
		assertThat(requestedPaths).hasSize(2)
		assertThat(requestedPaths).allSatisfy { path ->
			assertThat(path).contains("lat=47.20744")
			assertThat(path).contains("lng=-2.15987")
			assertThat(path).contains("start=1780531200")
			assertThat(path).contains("end=1780617600")
		}
		assertThat(authorizationHeaders).containsExactly("test-api-key", "test-api-key")
	}

	@Test
	fun `provider maps Stormglass sg sea level field`() {
		val provider = provider(apiKey = "test-api-key")

		val tide = provider.fetchTideDay(spot, LocalDate.parse("2026-06-04"))

		assertThat(tide.points[1].waterHeightMeters).isEqualTo(3.6)
	}

	@Test
	fun `provider declares missing api key without exposing a secret`() {
		val provider = provider(apiKey = "")

		assertThat(provider.unavailableReason()).isEqualTo(TideUnavailableReason.MISSING_API_KEY)
		assertThat(provider.unavailableMessage()).isEqualTo("Clé API Stormglass absente")
	}

	private fun provider(apiKey: String): StormglassTideProvider =
		StormglassTideProvider(
			properties = StormglassProperties(
				baseUrl = "http://127.0.0.1:${server.address.port}",
				apiKey = apiKey,
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
		val seaLevelJson = """
			{
			  "data": [
			    { "height": 3.2, "time": "2026-06-04T09:00:00+00:00" },
			    { "sg": 3.6, "time": "2026-06-04T10:00:00+00:00" }
			  ],
			  "meta": {
			    "station": {
			      "name": "Saint-Nazaire",
			      "distance": 12.4
			    }
			  }
			}
		""".trimIndent()

		val extremesJson = """
			{
			  "data": [
			    { "height": 1.1, "time": "2026-06-04T06:20:00+00:00", "type": "low" },
			    { "height": 4.8, "time": "2026-06-04T12:40:00+00:00", "type": "high" }
			  ],
			  "meta": {
			    "station": {
			      "name": "Saint-Nazaire",
			      "distance": 12.4
			    }
			  }
			}
		""".trimIndent()
	}
}
