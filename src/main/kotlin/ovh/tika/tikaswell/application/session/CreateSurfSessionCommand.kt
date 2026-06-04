package ovh.tika.tikaswell.application.session

import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.Spot
import java.time.Instant

data class CreateSurfSessionCommand(
	val spot: Spot,
	val startsAt: Instant,
	val endsAt: Instant,
	val rating: Rating,
	val notes: String?,
)
