package ovh.tika.tikaswell.application.tide

import ovh.tika.tikaswell.domain.ProviderCallLog
import ovh.tika.tikaswell.domain.ProviderCallPurpose
import ovh.tika.tikaswell.domain.ProviderCallResult
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class TideService(
	private val tideProvider: TideProvider,
	private val tideCacheRepository: TideCacheRepository,
	private val providerCallLogRepository: ProviderCallLogRepository,
	private val properties: TideProperties,
	private val clock: Clock,
) {
	fun getTideDay(spot: Spot, date: LocalDate): TideDayCache {
		val cached = tideCacheRepository.findBySpotIdAndDateAndProvider(spot.id, date, tideProvider.name)
		if (cached != null) {
			return cached
		}

		tideProvider.unavailableReason()?.let { reason ->
			return unavailable(
				spot = spot,
				date = date,
				reason = reason,
				message = tideProvider.unavailableMessage() ?: "Provider marée indisponible",
			)
		}

		if (!hasRemainingQuotaFor(tideProvider)) {
			return unavailable(spot, date, TideUnavailableReason.QUOTA_REACHED, "Quota marée atteint pour aujourd'hui")
		}

		return runCatching { tideProvider.fetchTideDay(spot, date) }
			.onSuccess { logProviderCall(spot, date, ProviderCallPurpose.TIDE_CACHE_MISS, ProviderCallResult.SUCCESS, null) }
			.onFailure { exception ->
				logProviderCall(
					spot = spot,
					date = date,
					purpose = ProviderCallPurpose.TIDE_CACHE_MISS,
					result = ProviderCallResult.FAILURE,
					message = exception.message,
				)
			}
			.getOrElse { exception ->
				unavailable(
					spot = spot,
					date = date,
					reason = TideUnavailableReason.PROVIDER_UNAVAILABLE,
					message = exception.message ?: "Provider marée indisponible",
				)
			}
			.let { cache ->
				if (cache.unavailableReason == null) {
					tideCacheRepository.save(cache)
				} else {
					cache
				}
			}
	}

	private fun hasRemainingQuotaFor(provider: TideProvider): Boolean {
		val startOfDay = clock.instant().atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)
		val callsToday = providerCallLogRepository.countByProviderNameAndCalledAtBetween(
			providerName = provider.name,
			startsAt = startOfDay,
			endsAt = startOfDay.plusSeconds(SECONDS_PER_DAY),
		)
		return callsToday + provider.requiredCallsPerFetch <= properties.maxProviderCallsPerDay
	}

	private fun logProviderCall(
		spot: Spot,
		date: LocalDate,
		purpose: ProviderCallPurpose,
		result: ProviderCallResult,
		message: String?,
	) {
		repeat(tideProvider.requiredCallsPerFetch) {
			providerCallLogRepository.save(
				ProviderCallLog(
					id = null,
					providerName = tideProvider.name,
					spotId = spot.id,
					calledForDate = date,
					calledAt = clock.instant(),
					purpose = purpose,
					result = result,
					message = message,
				),
			)
		}
	}

	private fun unavailable(
		spot: Spot,
		date: LocalDate,
		reason: TideUnavailableReason,
		message: String,
	): TideDayCache =
		TideDayCache(
			id = null,
			spotId = spot.id,
			date = date,
			providerName = tideProvider.name,
			fetchedAt = Instant.now(clock),
			stationName = null,
			stationDistanceKilometers = null,
			coefficient = null,
			unavailableReason = reason,
			unavailableMessage = message,
			points = emptyList(),
			events = emptyList(),
		)

	private companion object {
		const val SECONDS_PER_DAY = 86_400L
	}
}
