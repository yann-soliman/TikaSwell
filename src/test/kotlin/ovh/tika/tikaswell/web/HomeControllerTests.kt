package ovh.tika.tikaswell.web

import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.application.tide.ProviderCallLogRepository
import ovh.tika.tikaswell.application.tide.TideCacheRepository
import ovh.tika.tikaswell.application.tide.TideProvider
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.ProviderCallLog
import ovh.tika.tikaswell.domain.ProviderCallPurpose
import ovh.tika.tikaswell.domain.ProviderCallResult
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.TimeWindow
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TideEventType
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@Import(HomeControllerTests.TestConditionsProviderConfig::class)
class HomeControllerTests {
	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	@Autowired
	private lateinit var tideCacheRepository: TideCacheRepository

	@Autowired
	private lateinit var providerCallLogRepository: ProviderCallLogRepository

	@Autowired
	private lateinit var tideProvider: TestTideProvider

	@Autowired
	private lateinit var surfSessionRepository: SurfSessionRepository

	@BeforeEach
	fun cleanDatabase() {
		tideProvider.unavailableReason = null
		jdbcTemplate.update("DELETE FROM provider_call_log")
		jdbcTemplate.update("DELETE FROM tide_points")
		jdbcTemplate.update("DELETE FROM tide_events")
		jdbcTemplate.update("DELETE FROM tide_day_cache")
		jdbcTemplate.update("DELETE FROM condition_snapshots")
		jdbcTemplate.update("DELETE FROM surf_sessions")
	}

	@Test
	fun `home page renders configured spot and empty session history`() {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("href=\"/favicon.svg\"")))
			.andExpect(content().string(containsString("Initial spot")))
			.andExpect(content().string(containsString("Conditions actuelles")))
			.andExpect(content().string(containsString("Donnée du 04/06/2026 à 12:00")))
			.andExpect(content().string(containsString("18,0 km/h")))
			.andExpect(content().string(containsString("Rafales / période")))
			.andExpect(content().string(containsString("Période pic")))
			.andExpect(content().string(containsString("Houle")))
			.andExpect(content().string(containsString("Mer du vent")))
			.andExpect(content().string(containsString("Comment lire ce score ?")))
			.andExpect(content().string(containsString("Marée absente du cache pour cette date")))
			.andExpect(content().string(containsString("La marée est ignorée dans la similarité faute de données exploitables.")))
			.andExpect(content().string(containsString("Ajouter une session")))
			.andExpect(content().string(containsString("Aucune session enregistrée pour le moment.")))
	}

	@Test
	fun `home page renders Lovable v2 dashboard with same data`() {
		mockMvc.perform(get("/v2"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("href=\"/styles/app-v2.css\"")))
			.andExpect(content().string(containsString("TikaSwell")))
			.andExpect(content().string(containsString("V1")))
			.andExpect(content().string(containsString("V2")))
			.andExpect(content().string(containsString("V3")))
			.andExpect(content().string(containsString("Incertain")))
			.andExpect(content().string(containsString("Conditions en direct")))
			.andExpect(content().string(containsString("Nouvelle session")))
			.andExpect(content().string(containsString("Journal récent")))
			.andExpect(content().string(containsString("18,0 km/h")))
			.andExpect(content().string(containsString("Conditions actuelles")))
	}

	@Test
	fun `home page renders Lovable v3 mobile dashboard with same data`() {
		mockMvc.perform(get("/v3"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("href=\"/styles/app-v3.css\"")))
			.andExpect(content().string(containsString("TikaSwell")))
			.andExpect(content().string(containsString("V1")))
			.andExpect(content().string(containsString("V2")))
			.andExpect(content().string(containsString("V3")))
			.andExpect(content().string(containsString("Incertain")))
			.andExpect(content().string(containsString("Conditions live")))
			.andExpect(content().string(containsString("Pourquoi ce score ?")))
			.andExpect(content().string(containsString("Nouvelle session")))
			.andExpect(content().string(containsString("Statistiques")))
			.andExpect(content().string(containsString("18,0 km/h")))
			.andExpect(content().string(containsString("Mer du vent")))
			.andExpect(content().string(containsString("Score indisponible")))
			.andExpect(content().string(containsString("Session admin")))
			.andExpect(content().string(not(containsString("Salut Théo"))))
			.andExpect(content().string(not(containsString("GO surfer"))))
			.andExpect(content().string(not(containsString("21,4 °C"))))
			.andExpect(content().string(not(containsString("Peu de data"))))
			.andExpect(content().string(not(containsString("Fenêtre"))))
			.andExpect(content().string(not(containsString("Tendance"))))
	}

	@Test
	fun `home page renders available tide context from cache`() {
		tideCacheRepository.save(availableTideCache(LocalDate.parse("2026-06-04")))

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Marée")))
			.andExpect(content().string(containsString("Marée issue du cache local")))
			.andExpect(content().string(containsString("3,25 m")))
			.andExpect(content().string(containsString("Montante")))
			.andExpect(content().string(containsString("Pleine mer précédente")))
			.andExpect(content().string(containsString("Pleine mer suivante")))
			.andExpect(content().string(containsString("Basse mer précédente")))
			.andExpect(content().string(containsString("Basse mer suivante")))
			.andExpect(content().string(containsString("12:40")))
			.andExpect(content().string(containsString("18:55")))
			.andExpect(content().string(containsString("Saint-Nazaire · 12,4 km")))
			.andExpect(content().string(containsString("La marée est disponible, mais ignorée faute de données historiques comparables.")))
	}

	@Test
	fun `home page hides generic station name`() {
		tideCacheRepository.save(availableTideCache(LocalDate.parse("2026-06-04"), stationName = "station"))

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Station à 12,4 km")))
	}

	@Test
	fun `home page renders quota reached tide state without provider fetch`() {
		repeat(6) {
			providerCallLogRepository.save(
				ProviderCallLog(
					id = null,
					providerName = "api-maree.fr",
					spotId = SpotId("initial"),
					calledForDate = LocalDate.parse("2026-06-04"),
					calledAt = Instant.now(),
					purpose = ProviderCallPurpose.TIDE_CACHE_PREFETCH,
					result = ProviderCallResult.SUCCESS,
					message = null,
				),
			)
		}

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Quota marée atteint pour aujourd&#39;hui")))
	}

	@Test
	fun `home page renders provider unavailable tide state`() {
		tideProvider.unavailableReason = TideUnavailableReason.PROVIDER_UNAVAILABLE

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Provider marée indisponible pour le test")))
	}

	@Test
	fun `session form validates end time after start time`() {
		mockMvc.perform(
			post("/sessions")
				.param("date", "2026-06-04")
				.param("startTime", "10:00")
				.param("endTime", "09:00")
				.param("rating", "8")
				.param("notes", "Too short"),
		)
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("L&#39;heure de fin doit être après l&#39;heure de début")))
	}

	@Test
	fun `session form saves a valid session`() {
		mockMvc.perform(
			post("/sessions")
				.param("date", "2026-06-04")
				.param("startTime", "09:00")
				.param("endTime", "11:00")
				.param("rating", "8")
				.param("notes", "Clean morning lines"),
			)
			.andExpect(status().is3xxRedirection)
			.andExpect(redirectedUrl("/?saved=1"))

		mockMvc.perform(get("/").param("saved", "1"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Session enregistrée")))
			.andExpect(content().string(containsString("Clean morning lines")))
			.andExpect(content().string(containsString("Marée absente du cache pour cette date")))
			.andExpect(content().string(containsString("Provider de test")))
			.andExpect(content().string(containsString("Vent 18,0 km/h, rafales 25,0 km/h, dir. 260°")))
			.andExpect(content().string(containsString("Vagues 1,1 m, période 8,0 s, dir. 245°")))
			.andExpect(content().string(containsString("Score estimé")))
			.andExpect(content().string(containsString("Sessions historiques proches")))
			.andExpect(content().string(containsString("8")))
	}

	@Test
	fun `session form preserves Lovable v2 after save`() {
		mockMvc.perform(
			post("/sessions")
				.param("uiVersion", "v2")
				.param("date", "2026-06-04")
				.param("startTime", "09:00")
				.param("endTime", "11:00")
				.param("rating", "8")
				.param("notes", "Clean morning lines"),
		)
			.andExpect(status().is3xxRedirection)
			.andExpect(redirectedUrl("/v2?saved=1"))
	}

	@Test
	fun `session form preserves Lovable v3 after save`() {
		mockMvc.perform(
			post("/sessions")
				.param("uiVersion", "v3")
				.param("date", "2026-06-04")
				.param("startTime", "09:00")
				.param("endTime", "11:00")
				.param("rating", "8")
				.param("notes", "Clean morning lines"),
		)
			.andExpect(status().is3xxRedirection)
			.andExpect(redirectedUrl("/v3?saved=1"))

		mockMvc.perform(get("/v3").param("saved", "1"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Session enregistrée")))
			.andExpect(content().string(containsString("Clean morning lines")))
			.andExpect(content().string(containsString("Dernières sessions")))
	}

	@Test
	fun `session history renders a clear state when conditions were not captured`() {
		surfSessionRepository.save(
			SurfSession(
				id = null,
				spotId = SpotId("initial"),
				startsAt = Instant.parse("2026-06-04T09:00:00Z"),
				endsAt = Instant.parse("2026-06-04T11:00:00Z"),
				rating = Rating(7),
				notes = "Session sans snapshot",
			),
		)

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Session sans snapshot")))
			.andExpect(content().string(containsString("Conditions météo/marine non capturées")))
	}

	@Test
	fun `session history renders tide context from cache`() {
		tideCacheRepository.save(availableTideCache(LocalDate.parse("2026-06-04")))
		mockMvc.perform(
			post("/sessions")
				.param("date", "2026-06-04")
				.param("startTime", "09:00")
				.param("endTime", "11:00")
				.param("rating", "8")
				.param("notes", "Avec marée en cache"),
		)
			.andExpect(status().is3xxRedirection)

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Avec marée en cache")))
			.andExpect(content().string(containsString("3,25 m · Montante")))
			.andExpect(content().string(containsString("Pleine mer")))
			.andExpect(content().string(containsString("n/d -&gt; 12:40")))
			.andExpect(content().string(containsString("07:20 -&gt; 18:55")))
			.andExpect(content().string(containsString("La marée influence la similarité")))
	}

	private fun availableTideCache(date: LocalDate, stationName: String = "Saint-Nazaire"): TideDayCache =
		TideDayCache(
			id = null,
			spotId = SpotId("initial"),
			date = date,
			providerName = "api-maree.fr",
			fetchedAt = Instant.parse("2026-06-04T02:00:00Z"),
			stationName = stationName,
			stationDistanceKilometers = 12.4,
			coefficient = null,
			unavailableReason = null,
			unavailableMessage = null,
			points = listOf(
				TidePoint(Instant.parse("2026-06-04T08:00:00Z"), 3.25),
				TidePoint(Instant.parse("2026-06-04T09:00:00Z"), 3.0),
				TidePoint(Instant.parse("2026-06-04T10:00:00Z"), 3.25),
			),
			events = listOf(
				TideEvent(TideEventType.LOW, Instant.parse("2026-06-04T05:20:00Z"), 1.1),
				TideEvent(TideEventType.HIGH, Instant.parse("2026-06-04T10:40:00Z"), 4.8),
				TideEvent(TideEventType.LOW, Instant.parse("2026-06-04T16:55:00Z"), 1.2),
			),
		)

	@TestConfiguration
	class TestConditionsProviderConfig {
		@Bean
		@Primary
		fun testConditionsProvider(): ConditionsProvider =
			object : ConditionsProvider {
				override val name: String = "Provider de test"

				override fun fetchCurrentConditions(spot: Spot): CurrentConditions =
					CurrentConditions(
						spot = spot,
						fetchedAt = Instant.parse("2026-06-04T10:00:00Z"),
						snapshot = snapshot(spot),
					)

				override fun fetchHistoricalConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> =
					listOf(snapshot(spot))

				override fun fetchForecastConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> =
					listOf(snapshot(spot))

				private fun snapshot(spot: Spot): ConditionSnapshot =
					ConditionSnapshot(
						spotId = spot.id,
						timestamp = Instant.parse("2026-06-04T10:00:00Z"),
						windSpeedKmh = 18.0,
						windGustKmh = 25.0,
						windDirection = Direction(260),
						waveHeightMeters = 1.1,
						wavePeriodSeconds = 8.0,
						wavePeakPeriodSeconds = 11.0,
						waveDirection = Direction(245),
						windWaveHeightMeters = 0.4,
						windWavePeriodSeconds = 4.5,
						windWavePeakPeriodSeconds = 5.5,
						windWaveDirection = Direction(270),
						swellWaveHeightMeters = 1.0,
						swellWavePeriodSeconds = 10.0,
						swellWavePeakPeriodSeconds = 12.0,
						swellWaveDirection = Direction(250),
						providerName = name,
					)
			}

		@Bean
		@Primary
		fun testTideProvider(): TestTideProvider = TestTideProvider()
	}
}

class TestTideProvider : TideProvider {
	override val name: String = "api-maree.fr"
	override val requiredCallsPerFetch: Int = 1
	var unavailableReason: TideUnavailableReason? = null
	var fetchCount: Int = 0

	override fun unavailableReason(): TideUnavailableReason? = unavailableReason

	override fun unavailableMessage(): String? =
		unavailableReason?.let { "Provider marée indisponible pour le test" }

	override fun fetchTideDay(spot: Spot, date: LocalDate): TideDayCache {
		fetchCount += 1
		error("Le rendu web ne doit pas déclencher de fetch marée")
	}
}
