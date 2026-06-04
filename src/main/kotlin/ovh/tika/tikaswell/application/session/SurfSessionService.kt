package ovh.tika.tikaswell.application.session

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TimeWindow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SurfSessionService(
	private val repository: SurfSessionRepository,
	private val conditionsProvider: ConditionsProvider,
	private val conditionSnapshotRepository: ConditionSnapshotRepository,
) {
	fun create(command: CreateSurfSessionCommand): SurfSession {
		val session = SurfSession(
			id = null,
			spotId = command.spot.id,
			startsAt = command.startsAt,
			endsAt = command.endsAt,
			rating = command.rating,
			notes = command.notes?.trim()?.takeIf { it.isNotBlank() },
		)

		val saved = repository.save(session)
		captureConditions(command.spot, saved)
		return saved
	}

	fun listForInitialSpot(spotId: SpotId): List<SurfSession> =
		repository.findBySpotId(spotId)

	fun findById(id: ovh.tika.tikaswell.domain.SurfSessionId): SurfSession? =
		repository.findById(id)

	private fun captureConditions(spot: Spot, session: SurfSession) {
		try {
			conditionsProvider.fetchHistoricalConditions(
				spot = spot,
				TimeWindow(session.startsAt, session.endsAt),
			).forEach { snapshot ->
				conditionSnapshotRepository.saveForSession(session.id!!, snapshot)
			}
		} catch (exception: Exception) {
			// La session reste la donnée principale : une panne provider ne doit pas bloquer la saisie terrain.
			logger.warn("Impossible de capturer les conditions pour la session ${session.id?.value}", exception)
		}
	}

	private companion object {
		val logger = LoggerFactory.getLogger(SurfSessionService::class.java)
	}
}
