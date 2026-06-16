package ovh.tika.tikaswell.application.conditions

import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSessionId

interface ConditionSnapshotRepository {
	fun saveForSession(sessionId: SurfSessionId, snapshot: ConditionSnapshot): SessionConditionSnapshot

	fun findBySessionId(sessionId: SurfSessionId): List<SessionConditionSnapshot>

	fun findBySpotId(spotId: SpotId): List<SessionConditionSnapshot>

	fun deleteBySessionId(sessionId: SurfSessionId)
}
