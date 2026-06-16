package ovh.tika.tikaswell.application.session

import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.SurfSessionId

interface SurfSessionRepository {
	fun save(session: SurfSession): SurfSession

	fun update(session: SurfSession): SurfSession

	fun findById(id: SurfSessionId): SurfSession?

	fun findBySpotId(spotId: SpotId): List<SurfSession>

	fun deleteById(id: SurfSessionId)
}
