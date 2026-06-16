package ovh.tika.tikaswell.application.session

import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SurfSessionId
import java.time.Instant

data class UpdateSurfSessionCommand(
	val id: SurfSessionId,
	val spot: Spot,
	val startsAt: Instant,
	val endsAt: Instant,
	val rating: Rating,
	val notes: String?,
) {
	init {
		require(startsAt.isBefore(endsAt)) { "Le début de session doit être avant la fin" }
	}
}
