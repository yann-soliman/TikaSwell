package ovh.tika.tikaswell.infrastructure.openmeteo

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TimeWindow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OpenMeteoConditionsProviderTests {
	private lateinit var weatherServer: HttpServer
	private lateinit var marineServer: HttpServer

	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)

	@BeforeEach
	fun startServers() {
		weatherServer = testServer(weatherJson)
		marineServer = testServer(marineJson)
	}

	@AfterEach
	fun stopServers() {
		weatherServer.stop(0)
		marineServer.stop(0)
	}

	@Test
	fun `forecast conditions merge weather and marine Open-Meteo responses`() {
		val provider = providerAt("2026-06-04T10:20:00Z")
		val snapshots = provider.fetchForecastConditions(
			spot,
			TimeWindow(
				Instant.parse("2026-06-04T09:00:00Z"),
				Instant.parse("2026-06-04T11:00:00Z"),
			),
		)

		assertThat(snapshots).hasSize(2)
		assertThat(snapshots[0].windSpeedKmh).isEqualTo(14.0)
		assertThat(snapshots[0].windGustKmh).isEqualTo(21.0)
		assertThat(snapshots[0].windDirection?.degrees).isEqualTo(260)
		assertThat(snapshots[0].waveHeightMeters).isEqualTo(1.2)
		assertThat(snapshots[0].wavePeriodSeconds).isEqualTo(8.0)
		assertThat(snapshots[0].wavePeakPeriodSeconds).isEqualTo(11.0)
		assertThat(snapshots[0].waveDirection?.degrees).isEqualTo(245)
		assertThat(snapshots[0].swellWaveHeightMeters).isEqualTo(1.0)
		assertThat(snapshots[0].swellWavePeriodSeconds).isEqualTo(10.0)
		assertThat(snapshots[0].swellWavePeakPeriodSeconds).isEqualTo(12.0)
		assertThat(snapshots[0].swellWaveDirection?.degrees).isEqualTo(250)
		assertThat(snapshots[0].windWaveHeightMeters).isEqualTo(0.4)
		assertThat(snapshots[0].windWavePeriodSeconds).isEqualTo(4.0)
		assertThat(snapshots[0].windWavePeakPeriodSeconds).isEqualTo(5.0)
		assertThat(snapshots[0].windWaveDirection?.degrees).isEqualTo(275)
		assertThat(snapshots[0].providerName).isEqualTo("Open-Meteo")
	}

	@Test
	fun `current conditions select the closest canonical snapshot`() {
		val provider = providerAt("2026-06-04T10:20:00Z")

		val current = provider.fetchCurrentConditions(spot)

		assertThat(current.spot).isEqualTo(spot)
		assertThat(current.snapshot.timestamp).isEqualTo(Instant.parse("2026-06-04T10:00:00Z"))
		assertThat(current.snapshot.windSpeedKmh).isEqualTo(16.0)
	}

	private fun providerAt(instant: String): OpenMeteoConditionsProvider =
		OpenMeteoConditionsProvider(
			properties = OpenMeteoProperties(
				weatherBaseUrl = "http://127.0.0.1:${weatherServer.address.port}",
				marineBaseUrl = "http://127.0.0.1:${marineServer.address.port}",
			),
			restTemplateBuilder = RestTemplateBuilder(),
			clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
		)

	private fun testServer(body: String): HttpServer =
		HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
			createContext("/") { exchange -> exchange.respondJson(body) }
			start()
		}

	private fun HttpExchange.respondJson(body: String) {
		val bytes = body.toByteArray()
		responseHeaders.add("Content-Type", "application/json")
		sendResponseHeaders(200, bytes.size.toLong())
		responseBody.use { it.write(bytes) }
	}

	private companion object {
		val weatherJson = """
			{
			  "hourly": {
			    "time": ["2026-06-04T09:00", "2026-06-04T10:00"],
			    "wind_speed_10m": [14.0, 16.0],
			    "wind_gusts_10m": [21.0, 24.0],
			    "wind_direction_10m": [260, 270]
			  }
			}
		""".trimIndent()

		val marineJson = """
			{
			  "hourly": {
			    "time": ["2026-06-04T09:00", "2026-06-04T12:00"],
			    "wave_height": [1.2, 1.4],
			    "wave_period": [8.0, 9.0],
			    "wave_peak_period": [11.0, 12.0],
			    "wave_direction": [245, 250],
			    "swell_wave_height": [1.0, 1.1],
			    "swell_wave_period": [10.0, 10.5],
			    "swell_wave_peak_period": [12.0, 13.0],
			    "swell_wave_direction": [250, 252],
			    "wind_wave_height": [0.4, 0.5],
			    "wind_wave_period": [4.0, 4.5],
			    "wind_wave_peak_period": [5.0, 5.5],
			    "wind_wave_direction": [275, 280]
			  }
			}
		""".trimIndent()
	}
}
