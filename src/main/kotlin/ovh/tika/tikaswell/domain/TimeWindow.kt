package ovh.tika.tikaswell.domain

import java.time.Instant

data class TimeWindow(
	val startsAt: Instant,
	val endsAt: Instant,
) {
	init {
		require(startsAt.isBefore(endsAt)) { "Window start must be before window end" }
	}
}
