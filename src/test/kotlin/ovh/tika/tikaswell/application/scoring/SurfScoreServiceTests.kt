package ovh.tika.tikaswell.application.scoring

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.SurfSessionId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SurfScoreServiceTests {
	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)

	private val clock = Clock.fixed(Instant.parse("2026-06-04T12:00:00Z"), ZoneOffset.UTC)

	@Test
	fun `score is empty without historical snapshots`() {
		val service = SurfScoreService(
			conditionSnapshotRepository = InMemoryConditionSnapshotRepository(),
			surfSessionRepository = InMemorySurfSessionRepository(),
			clock = clock,
		)

		val score = service.score(CurrentConditions(spot, clock.instant(), currentSnapshot()))

		assertThat(score.score).isNull()
		assertThat(score.confidence).isEqualTo(0.0)
		assertThat(score.contributors).isEmpty()
	}

	@Test
	fun `score uses nearest historical sessions and exposes contributors`() {
		val sessions = InMemorySurfSessionRepository()
		val snapshots = InMemoryConditionSnapshotRepository()
		val service = SurfScoreService(snapshots, sessions, clock)

		val goodSession = sessions.save(session(rating = 9))
		val badSession = sessions.save(session(rating = 3))
		snapshots.saveForSession(goodSession.id!!, currentSnapshot(windSpeedKmh = 16.0, waveHeightMeters = 1.1))
		snapshots.saveForSession(badSession.id!!, currentSnapshot(windSpeedKmh = 35.0, waveHeightMeters = 0.2))

		val score = service.score(CurrentConditions(spot, clock.instant(), currentSnapshot(windSpeedKmh = 15.0, waveHeightMeters = 1.0)))

		assertThat(score.score).isNotNull()
		assertThat(score.score!!).isGreaterThan(6.0)
		assertThat(score.contributors.first().sessionId).isEqualTo(goodSession.id)
		assertThat(score.contributors.first().similarity).isGreaterThan(score.contributors.last().similarity)
	}

	private fun session(rating: Int): SurfSession =
		SurfSession(
			id = null,
			spotId = spot.id,
			startsAt = Instant.parse("2026-06-04T09:00:00Z"),
			endsAt = Instant.parse("2026-06-04T11:00:00Z"),
			rating = Rating(rating),
			notes = null,
		)

	private fun currentSnapshot(
		windSpeedKmh: Double = 15.0,
		waveHeightMeters: Double = 1.0,
	): ConditionSnapshot =
		ConditionSnapshot(
			spotId = spot.id,
			timestamp = Instant.parse("2026-06-04T10:00:00Z"),
			windSpeedKmh = windSpeedKmh,
			windGustKmh = 22.0,
			windDirection = Direction(265),
			waveHeightMeters = waveHeightMeters,
			wavePeriodSeconds = 8.0,
			waveDirection = Direction(245),
			providerName = "Open-Meteo",
		)
}

private class InMemorySurfSessionRepository : SurfSessionRepository {
	private val sessions = linkedMapOf<SurfSessionId, SurfSession>()
	private var nextId = 1L

	override fun save(session: SurfSession): SurfSession {
		val saved = session.copy(id = SurfSessionId(nextId++))
		sessions[saved.id!!] = saved
		return saved
	}

	override fun findById(id: SurfSessionId): SurfSession? =
		sessions[id]

	override fun findBySpotId(spotId: SpotId): List<SurfSession> =
		sessions.values.filter { it.spotId == spotId }
}

private class InMemoryConditionSnapshotRepository : ConditionSnapshotRepository {
	private val snapshots = mutableListOf<SessionConditionSnapshot>()
	private var nextId = 1L

	override fun saveForSession(sessionId: SurfSessionId, snapshot: ConditionSnapshot): SessionConditionSnapshot {
		val saved = SessionConditionSnapshot(
			id = ovh.tika.tikaswell.domain.ConditionSnapshotId(nextId++),
			sessionId = sessionId,
			snapshot = snapshot,
		)
		snapshots += saved
		return saved
	}

	override fun findBySessionId(sessionId: SurfSessionId): List<SessionConditionSnapshot> =
		snapshots.filter { it.sessionId == sessionId }

	override fun findBySpotId(spotId: SpotId): List<SessionConditionSnapshot> =
		snapshots.filter { it.snapshot.spotId == spotId }
}
