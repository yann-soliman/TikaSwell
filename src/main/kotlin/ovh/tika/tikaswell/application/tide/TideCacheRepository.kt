package ovh.tika.tikaswell.application.tide

import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideDayCache
import java.time.LocalDate

interface TideCacheRepository {
	fun save(cache: TideDayCache): TideDayCache

	fun findBySpotIdAndDateAndProvider(spotId: SpotId, date: LocalDate, providerName: String): TideDayCache?

	fun existsBySpotIdAndDateAndProvider(spotId: SpotId, date: LocalDate, providerName: String): Boolean
}
