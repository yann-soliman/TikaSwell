package ovh.tika.tikaswell.web

import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TimeWindow
import org.hamcrest.Matchers.containsString
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(HomeControllerTests.TestConditionsProviderConfig::class)
class HomeControllerTests {
	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	@BeforeEach
	fun cleanDatabase() {
		jdbcTemplate.update("DELETE FROM condition_snapshots")
		jdbcTemplate.update("DELETE FROM surf_sessions")
	}

	@Test
	fun `home page renders configured spot and empty session history`() {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Initial spot")))
			.andExpect(content().string(containsString("Conditions actuelles")))
			.andExpect(content().string(containsString("18,0 km/h")))
			.andExpect(content().string(containsString("Période moyenne")))
			.andExpect(content().string(containsString("Période pic")))
			.andExpect(content().string(containsString("Houle")))
			.andExpect(content().string(containsString("Mer du vent")))
			.andExpect(content().string(containsString("Comment lire ce score ?")))
			.andExpect(content().string(containsString("Ajouter une session")))
			.andExpect(content().string(containsString("Aucune session enregistrée pour le moment.")))
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
			.andExpect(redirectedUrl("/"))

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Clean morning lines")))
			.andExpect(content().string(containsString("Score estimé")))
			.andExpect(content().string(containsString("Sessions qui influencent le score")))
			.andExpect(content().string(containsString("8")))
	}

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
	}
}
