package ovh.tika.tikaswell.application.session

import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.SpotId
import java.time.Instant

data class CreateSurfSessionCommand(
	val spotId: SpotId,
	val startsAt: Instant,
	val endsAt: Instant,
	val rating: Rating,
	val notes: String?,
)
