package ovh.tika.tikaswell.application.tide

import ovh.tika.tikaswell.domain.ProviderCallLog
import java.time.Instant

interface ProviderCallLogRepository {
	fun save(call: ProviderCallLog): ProviderCallLog

	fun countByProviderNameAndCalledAtBetween(providerName: String, startsAt: Instant, endsAt: Instant): Int
}
