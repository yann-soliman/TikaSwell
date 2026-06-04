package ovh.tika.tikaswell.domain

import java.time.Instant

@JvmInline
value class SurfSessionId(val value: Long) {
	init {
		require(value > 0) { "L'id de session doit être positif" }
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
		require(startsAt.isBefore(endsAt)) { "Le début de session doit être avant la fin" }
	}
}
