package ovh.tika.tikaswell.application.conditions

import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.ConditionSnapshotId
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.SurfSessionId
import ovh.tika.tikaswell.domain.TimeWindow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HistoricalConditionsBackfillServiceTests {
	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)
	private val clock = Clock.fixed(Instant.parse("2026-06-10T10:00:00Z"), ZoneOffset.UTC)

	@Test
	fun `backfill stores historical conditions for recent sessions without snapshots`() {
		val sessions = InMemorySurfSessionRepository().apply {
			save(session("2026-05-21T16:30:00Z", "2026-05-21T18:30:00Z"))
		}
		val snapshots = InMemoryConditionSnapshotRepository()
		val provider = RecordingConditionsProvider()
		val service = service(sessions, snapshots, provider)

		val result = service.backfillMissingSessionConditions(spot)

		assertThat(result.scannedSessions).isEqualTo(1)
		assertThat(result.backfilledSessions).isEqualTo(1)
		assertThat(result.failedSessions).isZero()
		assertThat(provider.requestedWindows).hasSize(1)
		assertThat(snapshots.findBySpotId(spot.id)).hasSize(1)
	}

	@Test
	fun `backfill skips sessions that already have snapshots`() {
		val sessions = InMemorySurfSessionRepository()
		val savedSession = sessions.save(session("2026-05-21T16:30:00Z", "2026-05-21T18:30:00Z"))
		val snapshots = InMemoryConditionSnapshotRepository().apply {
			saveForSession(savedSession.id!!, snapshot())
		}
		val provider = RecordingConditionsProvider()
		val service = service(sessions, snapshots, provider)

		val result = service.backfillMissingSessionConditions(spot)

		assertThat(result.scannedSessions).isEqualTo(1)
		assertThat(result.backfilledSessions).isZero()
		assertThat(provider.requestedWindows).isEmpty()
		assertThat(snapshots.findBySessionId(savedSession.id!!)).hasSize(1)
	}

	@Test
	fun `backfill ignores sessions outside configured historical window`() {
		val sessions = InMemorySurfSessionRepository().apply {
			save(session("2026-04-01T16:30:00Z", "2026-04-01T18:30:00Z"))
		}
		val snapshots = InMemoryConditionSnapshotRepository()
		val provider = RecordingConditionsProvider()
		val service = service(sessions, snapshots, provider)

		val result = service.backfillMissingSessionConditions(spot)

		assertThat(result.scannedSessions).isZero()
		assertThat(result.backfilledSessions).isZero()
		assertThat(provider.requestedWindows).isEmpty()
	}

	private fun service(
		sessions: InMemorySurfSessionRepository,
		snapshots: InMemoryConditionSnapshotRepository,
		provider: RecordingConditionsProvider,
	): HistoricalConditionsBackfillService =
		HistoricalConditionsBackfillService(
			surfSessionRepository = sessions,
			conditionSnapshotRepository = snapshots,
			conditionsProvider = provider,
			properties = HistoricalConditionsBackfillProperties(daysBefore = 30, zone = "UTC"),
			clock = clock,
		)

	private fun session(startsAt: String, endsAt: String): SurfSession =
		SurfSession(
			id = null,
			spotId = spot.id,
			startsAt = Instant.parse(startsAt),
			endsAt = Instant.parse(endsAt),
			rating = Rating(8),
			notes = null,
		)

	private fun snapshot(): ConditionSnapshot =
		ConditionSnapshot(
			spotId = spot.id,
			timestamp = Instant.parse("2026-05-21T17:00:00Z"),
			windSpeedKmh = 18.0,
			windGustKmh = 25.0,
			windDirection = Direction(260),
			waveHeightMeters = 1.1,
			wavePeriodSeconds = 8.0,
			waveDirection = Direction(245),
			providerName = "Open-Meteo",
		)

	private inner class RecordingConditionsProvider : ConditionsProvider {
		override val name: String = "Open-Meteo"
		val requestedWindows = mutableListOf<TimeWindow>()

		override fun fetchCurrentConditions(spot: Spot): CurrentConditions =
			CurrentConditions(spot, clock.instant(), snapshot())

		override fun fetchHistoricalConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> {
			requestedWindows += window
			return listOf(snapshot())
		}

		override fun fetchForecastConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> =
			listOf(snapshot())
	}
}

private class InMemorySurfSessionRepository : SurfSessionRepository {
	private val sessions = linkedMapOf<SurfSessionId, SurfSession>()
	private var nextId = 1L

	override fun save(session: SurfSession): SurfSession {
		val saved = session.copy(id = SurfSessionId(nextId++))
		sessions[saved.id!!] = saved
		return saved
	}

	override fun update(session: SurfSession): SurfSession {
		sessions[session.id!!] = session
		return session
	}

	override fun findById(id: SurfSessionId): SurfSession? =
		sessions[id]

	override fun findBySpotId(spotId: SpotId): List<SurfSession> =
		sessions.values.filter { it.spotId == spotId }

	override fun deleteById(id: SurfSessionId) {
		sessions.remove(id)
	}
}

private class InMemoryConditionSnapshotRepository : ConditionSnapshotRepository {
	private val snapshots = mutableListOf<SessionConditionSnapshot>()
	private var nextId = 1L

	override fun saveForSession(sessionId: SurfSessionId, snapshot: ConditionSnapshot): SessionConditionSnapshot {
		val saved = SessionConditionSnapshot(
			id = ConditionSnapshotId(nextId++),
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

	override fun deleteBySessionId(sessionId: SurfSessionId) {
		snapshots.removeIf { it.sessionId == sessionId }
	}
}
