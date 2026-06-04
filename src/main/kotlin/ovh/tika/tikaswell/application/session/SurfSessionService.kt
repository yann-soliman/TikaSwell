package ovh.tika.tikaswell.application.session

import ovh.tika.tikaswell.domain.SurfSession
import org.springframework.stereotype.Service

@Service
class SurfSessionService(
	private val repository: SurfSessionRepository,
) {
	fun create(command: CreateSurfSessionCommand): SurfSession {
		val session = SurfSession(
			id = null,
			spotId = command.spotId,
			startsAt = command.startsAt,
			endsAt = command.endsAt,
			rating = command.rating,
			notes = command.notes?.trim()?.takeIf { it.isNotBlank() },
		)

		return repository.save(session)
	}

	fun listForInitialSpot(spotId: ovh.tika.tikaswell.domain.SpotId): List<SurfSession> =
		repository.findBySpotId(spotId)
}
