package ovh.tika.tikaswell.application.session

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.domain.ConditionSnapshot
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
import java.time.Instant

class SurfSessionServiceTests {
	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)

	@Test
	fun `create stores the session and captures historical conditions`() {
		val sessionRepository = InMemorySurfSessionRepository()
		val snapshotRepository = InMemoryConditionSnapshotRepository()
		val service = SurfSessionService(
			repository = sessionRepository,
			conditionsProvider = FixedConditionsProvider(),
			conditionSnapshotRepository = snapshotRepository,
		)

		val session = service.create(command())

		assertThat(session.id).isNotNull()
		assertThat(sessionRepository.findBySpotId(spot.id)).containsExactly(session)
		assertThat(snapshotRepository.findBySessionId(session.id!!)).hasSize(1)
	}

	@Test
	fun `create keeps the session when condition capture fails`() {
		val sessionRepository = InMemorySurfSessionRepository()
		val snapshotRepository = InMemoryConditionSnapshotRepository()
		val service = SurfSessionService(
			repository = sessionRepository,
			conditionsProvider = FailingConditionsProvider(),
			conditionSnapshotRepository = snapshotRepository,
		)

		val session = service.create(command())

		assertThat(session.id).isNotNull()
		assertThat(sessionRepository.findBySpotId(spot.id)).containsExactly(session)
		assertThat(snapshotRepository.findBySessionId(session.id!!)).isEmpty()
	}

	private fun command(): CreateSurfSessionCommand =
		CreateSurfSessionCommand(
			spot = spot,
			startsAt = Instant.parse("2026-06-04T09:00:00Z"),
			endsAt = Instant.parse("2026-06-04T11:00:00Z"),
			rating = Rating(8),
			notes = "Session propre",
		)
}

private class FixedConditionsProvider : ConditionsProvider {
	override val name: String = "Provider de test"

	override fun fetchCurrentConditions(spot: Spot): CurrentConditions =
		CurrentConditions(spot, Instant.parse("2026-06-04T10:00:00Z"), snapshot(spot))

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
			waveDirection = Direction(245),
			providerName = name,
		)
}

private class FailingConditionsProvider : ConditionsProvider {
	override val name: String = "Provider en échec"

	override fun fetchCurrentConditions(spot: Spot): CurrentConditions =
		error("Provider indisponible")

	override fun fetchHistoricalConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> =
		error("Provider indisponible")

	override fun fetchForecastConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot> =
		error("Provider indisponible")
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
