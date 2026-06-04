package ovh.tika.tikaswell.domain

import java.time.Instant

@JvmInline
value class SurfSessionId(val value: Long) {
	init {
		require(value > 0) { "Surf session id must be positive" }
	}
}

data class SurfSession(
	val id: SurfSessionId?,
	val spotId: SpotId,
	val startsAt: Instant,
	val endsAt: Instant,
	val rating: Rating,
	val notes: String?,
) {
	init {
		require(startsAt.isBefore(endsAt)) { "Session start must be before session end" }
	}
}
