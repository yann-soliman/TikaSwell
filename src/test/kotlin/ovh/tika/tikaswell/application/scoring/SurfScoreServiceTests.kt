package ovh.tika.tikaswell.application.scoring

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.application.tide.TideSnapshotLookup
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.SurfSessionId
import ovh.tika.tikaswell.domain.TidePhase
import ovh.tika.tikaswell.domain.TideSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
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
			tideSnapshotLookup = NoopTideSnapshotLookup(),
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
		val service = SurfScoreService(snapshots, sessions, NoopTideSnapshotLookup(), clock)

		val goodSession = sessions.save(session(rating = 9))
		val badSession = sessions.save(session(rating = 3))
		snapshots.saveForSession(goodSession.id!!, currentSnapshot(windSpeedKmh = 16.0, waveHeightMeters = 1.1))
		snapshots.saveForSession(badSession.id!!, currentSnapshot(windSpeedKmh = 35.0, waveHeightMeters = 0.2))

		val score = service.score(CurrentConditions(spot, clock.instant(), currentSnapshot(windSpeedKmh = 15.0, waveHeightMeters = 1.0)))

		assertThat(score.score).isNotNull()
		assertThat(score.score!!).isGreaterThan(6.0)
		assertThat(score.contributors.first().sessionId).isEqualTo(goodSession.id)
		assertThat(score.contributors.first().similarity).isGreaterThan(score.contributors.last().similarity)
		assertThat(score.tideUsed).isFalse()
	}

	@Test
	fun `score uses tide features when current and historical tides are available`() {
		val sessions = InMemorySurfSessionRepository()
		val snapshots = InMemoryConditionSnapshotRepository()
		val tideLookup = InMemoryTideSnapshotLookup()
		val service = SurfScoreService(snapshots, sessions, tideLookup, clock)

		val matchingTideSession = sessions.save(session(rating = 9))
		val oppositeTideSession = sessions.save(session(rating = 3))
		val currentSnapshot = currentSnapshot(timestamp = Instant.parse("2026-06-04T10:00:00Z"))
		snapshots.saveForSession(matchingTideSession.id!!, currentSnapshot(timestamp = Instant.parse("2026-06-04T10:00:00Z")))
		snapshots.saveForSession(oppositeTideSession.id!!, currentSnapshot(timestamp = Instant.parse("2026-06-04T16:00:00Z")))
		tideLookup.save(tideSnapshot(timestamp = Instant.parse("2026-06-04T10:00:00Z"), waterHeightMeters = 3.2, phase = TidePhase.RISING))
		tideLookup.save(tideSnapshot(timestamp = Instant.parse("2026-06-04T16:00:00Z"), waterHeightMeters = 0.7, phase = TidePhase.FALLING))

		val score = service.score(CurrentConditions(spot, clock.instant(), currentSnapshot))

		assertThat(score.tideUsed).isTrue()
		assertThat(score.contributors.first().sessionId).isEqualTo(matchingTideSession.id)
		assertThat(score.contributors.first().tideUsed).isTrue()
		assertThat(score.contributors.first().similarity).isGreaterThan(score.contributors.last().similarity)
	}

	@Test
	fun `score ignores tide features when tide is missing on one side`() {
		val sessions = InMemorySurfSessionRepository()
		val snapshots = InMemoryConditionSnapshotRepository()
		val tideLookup = InMemoryTideSnapshotLookup()
		val service = SurfScoreService(snapshots, sessions, tideLookup, clock)

		val session = sessions.save(session(rating = 8))
		snapshots.saveForSession(session.id!!, currentSnapshot(timestamp = Instant.parse("2026-06-04T10:00:00Z")))

		val score = service.score(CurrentConditions(spot, clock.instant(), currentSnapshot(timestamp = Instant.parse("2026-06-04T10:00:00Z"))))

		assertThat(score.score).isNotNull()
		assertThat(score.tideUsed).isFalse()
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
		timestamp: Instant = Instant.parse("2026-06-04T10:00:00Z"),
	): ConditionSnapshot =
		ConditionSnapshot(
			spotId = spot.id,
			timestamp = timestamp,
			windSpeedKmh = windSpeedKmh,
			windGustKmh = 22.0,
			windDirection = Direction(265),
			waveHeightMeters = waveHeightMeters,
			wavePeriodSeconds = 8.0,
			waveDirection = Direction(245),
			providerName = "Open-Meteo",
		)

	private fun tideSnapshot(
		timestamp: Instant,
		waterHeightMeters: Double,
		phase: TidePhase,
	): TideSnapshot =
		TideSnapshot(
			spotId = spot.id,
			timestamp = timestamp,
			waterHeightMeters = waterHeightMeters,
			phase = phase,
			previousHighTide = null,
			previousLowTide = null,
			nextHighTide = null,
			nextLowTide = null,
			timeSincePreviousHighTide = Duration.ofHours(2),
			timeSincePreviousLowTide = Duration.ofHours(4),
			timeUntilNextHighTide = Duration.ofHours(3),
			timeUntilNextLowTide = Duration.ofHours(5),
			coefficient = null,
			providerName = "Stormglass",
		)
}

private class NoopTideSnapshotLookup : TideSnapshotLookup {
	override fun snapshotAt(spot: Spot, instant: Instant): TideSnapshot? = null
}

private class InMemoryTideSnapshotLookup : TideSnapshotLookup {
	private val snapshots = mutableMapOf<Instant, TideSnapshot>()

	fun save(snapshot: TideSnapshot) {
		snapshots[snapshot.timestamp] = snapshot
	}

	override fun snapshotAt(spot: Spot, instant: Instant): TideSnapshot? =
		snapshots[instant]
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
