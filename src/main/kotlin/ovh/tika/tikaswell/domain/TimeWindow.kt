package ovh.tika.tikaswell.domain

import java.time.Instant

data class TimeWindow(
	val startsAt: Instant,
	val endsAt: Instant,
) {
	init {
		require(startsAt.isBefore(endsAt)) { "Le début de fenêtre doit être avant la fin" }
	}
}
