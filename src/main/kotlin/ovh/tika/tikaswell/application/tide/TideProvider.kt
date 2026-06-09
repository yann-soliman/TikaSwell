package ovh.tika.tikaswell.application.tide

import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideUnavailableReason
import java.time.LocalDate

interface TideProvider {
	val name: String

	val requiredCallsPerFetch: Int

	fun unavailableReason(): TideUnavailableReason? = null

	fun unavailableMessage(): String? = null

	fun fetchTideDay(spot: Spot, date: LocalDate): TideDayCache
}
