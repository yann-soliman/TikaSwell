package ovh.tika.tikaswell.infrastructure.persistence

import ovh.tika.tikaswell.application.tide.TideCacheRepository
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TideEventType
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
class JdbcTideCacheRepositoryTests {
	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	@Autowired
	private lateinit var repository: TideCacheRepository

	private val spotId = SpotId("ermitage")
	private val date = LocalDate.parse("2026-06-04")

	@BeforeEach
	fun cleanDatabase() {
		jdbcTemplate.update("DELETE FROM tide_points")
		jdbcTemplate.update("DELETE FROM tide_events")
		jdbcTemplate.update("DELETE FROM tide_day_cache")
	}

	@Test
	fun `tide day cache stores points events and provider metadata`() {
		val cache = availableCache()

		val saved = repository.save(cache)

		assertThat(saved.id).isNotNull()
		assertThat(repository.existsBySpotIdAndDateAndProvider(spotId, date, "api-maree.fr")).isTrue()

		val persisted = repository.findBySpotIdAndDateAndProvider(spotId, date, "api-maree.fr")

		assertThat(persisted).isNotNull()
		assertThat(persisted!!.stationName).isEqualTo("Saint-Nazaire")
		assertThat(persisted.stationDistanceKilometers).isEqualTo(12.4)
		assertThat(persisted.coefficient).isEqualTo(82.0)
		assertThat(persisted.points).containsExactly(
			TidePoint(Instant.parse("2026-06-04T09:00:00Z"), 3.2),
			TidePoint(Instant.parse("2026-06-04T10:00:00Z"), 3.6),
		)
		assertThat(persisted.events).containsExactly(
			TideEvent(TideEventType.LOW, Instant.parse("2026-06-04T06:20:00Z"), 1.1),
			TideEvent(TideEventType.HIGH, Instant.parse("2026-06-04T12:40:00Z"), 4.8),
		)
	}

	@Test
	fun `saving the same spot date and provider replaces previous cache`() {
		repository.save(availableCache())

		val unavailable = TideDayCache(
			id = null,
			spotId = spotId,
			date = date,
			providerName = "api-maree.fr",
			fetchedAt = Instant.parse("2026-06-04T02:00:00Z"),
			stationName = null,
			stationDistanceKilometers = null,
			coefficient = null,
			unavailableReason = TideUnavailableReason.QUOTA_REACHED,
			unavailableMessage = "Quota provider atteint",
			points = emptyList(),
			events = emptyList(),
		)

		repository.save(unavailable)

		val persisted = repository.findBySpotIdAndDateAndProvider(spotId, date, "api-maree.fr")!!
		assertThat(persisted.unavailableReason).isEqualTo(TideUnavailableReason.QUOTA_REACHED)
		assertThat(persisted.unavailableMessage).isEqualTo("Quota provider atteint")
		assertThat(persisted.points).isEmpty()
		assertThat(persisted.events).isEmpty()
	}

	private fun availableCache(): TideDayCache =
		TideDayCache(
			id = null,
			spotId = spotId,
			date = date,
			providerName = "api-maree.fr",
			fetchedAt = Instant.parse("2026-06-04T01:00:00Z"),
			stationName = "Saint-Nazaire",
			stationDistanceKilometers = 12.4,
			coefficient = 82.0,
			unavailableReason = null,
			unavailableMessage = null,
			points = listOf(
				TidePoint(Instant.parse("2026-06-04T09:00:00Z"), 3.2),
				TidePoint(Instant.parse("2026-06-04T10:00:00Z"), 3.6),
			),
			events = listOf(
				TideEvent(TideEventType.LOW, Instant.parse("2026-06-04T06:20:00Z"), 1.1),
				TideEvent(TideEventType.HIGH, Instant.parse("2026-06-04T12:40:00Z"), 4.8),
			),
		)
}
