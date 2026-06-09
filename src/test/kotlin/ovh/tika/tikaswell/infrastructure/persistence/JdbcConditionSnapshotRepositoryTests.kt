package ovh.tika.tikaswell.infrastructure.persistence

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

@SpringBootTest
class JdbcConditionSnapshotRepositoryTests {
	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	@Autowired
	private lateinit var sessionRepository: SurfSessionRepository

	@Autowired
	private lateinit var snapshotRepository: ConditionSnapshotRepository

	private val spotId = SpotId("initial")

	@BeforeEach
	fun cleanDatabase() {
		jdbcTemplate.update("DELETE FROM condition_snapshots")
		jdbcTemplate.update("DELETE FROM surf_sessions")
	}

	@Test
	fun `snapshot is persisted and associated with a surf session`() {
		val session = savedSession()
		val sessionId = session.id!!
		val snapshot = ConditionSnapshot(
			spotId = spotId,
			timestamp = Instant.parse("2026-06-04T09:00:00Z"),
			windSpeedKmh = 18.5,
			windGustKmh = 26.0,
			windDirection = Direction(260),
			waveHeightMeters = 1.1,
			wavePeriodSeconds = 8.5,
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
			providerName = "Open-Meteo",
		)

		val savedSnapshot = snapshotRepository.saveForSession(sessionId, snapshot)

		assertThat(savedSnapshot.id).isNotNull()
		assertThat(savedSnapshot.sessionId).isEqualTo(sessionId)
		assertThat(snapshotRepository.findBySessionId(sessionId)).containsExactly(savedSnapshot)
		assertThat(snapshotRepository.findBySpotId(spotId)).containsExactly(savedSnapshot)
		assertThat(snapshotRepository.findBySessionId(sessionId).single().snapshot.swellWavePeakPeriodSeconds).isEqualTo(12.0)
	}

	@Test
	fun `snapshot keeps nullable marine values when provider has gaps`() {
		val session = savedSession()
		val sessionId = session.id!!
		val snapshot = ConditionSnapshot(
			spotId = spotId,
			timestamp = Instant.parse("2026-06-04T10:00:00Z"),
			windSpeedKmh = 12.0,
			windGustKmh = null,
			windDirection = null,
			waveHeightMeters = null,
			wavePeriodSeconds = null,
			wavePeakPeriodSeconds = null,
			waveDirection = null,
			windWaveHeightMeters = null,
			windWavePeriodSeconds = null,
			windWavePeakPeriodSeconds = null,
			windWaveDirection = null,
			swellWaveHeightMeters = null,
			swellWavePeriodSeconds = null,
			swellWavePeakPeriodSeconds = null,
			swellWaveDirection = null,
			providerName = "Open-Meteo",
		)

		snapshotRepository.saveForSession(sessionId, snapshot)

		val persisted = snapshotRepository.findBySessionId(sessionId).single().snapshot
		assertThat(persisted.windGustKmh).isNull()
		assertThat(persisted.windDirection).isNull()
		assertThat(persisted.waveHeightMeters).isNull()
		assertThat(persisted.wavePeriodSeconds).isNull()
		assertThat(persisted.wavePeakPeriodSeconds).isNull()
		assertThat(persisted.waveDirection).isNull()
		assertThat(persisted.windWaveHeightMeters).isNull()
		assertThat(persisted.windWavePeriodSeconds).isNull()
		assertThat(persisted.windWavePeakPeriodSeconds).isNull()
		assertThat(persisted.windWaveDirection).isNull()
		assertThat(persisted.swellWaveHeightMeters).isNull()
		assertThat(persisted.swellWavePeriodSeconds).isNull()
		assertThat(persisted.swellWavePeakPeriodSeconds).isNull()
		assertThat(persisted.swellWaveDirection).isNull()
	}

	private fun savedSession(): SurfSession =
		sessionRepository.save(
			SurfSession(
				id = null,
				spotId = spotId,
				startsAt = Instant.parse("2026-06-04T09:00:00Z"),
				endsAt = Instant.parse("2026-06-04T11:00:00Z"),
				rating = Rating(8),
				notes = "Session de test",
			),
		)
}
