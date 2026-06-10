package ovh.tika.tikaswell.application.tide

import ovh.tika.tikaswell.domain.ProviderCallLog
import ovh.tika.tikaswell.domain.ProviderCallPurpose
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TideServiceTests {
	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)
	private val date = LocalDate.parse("2026-06-04")
	private val clock = Clock.fixed(Instant.parse("2026-06-04T10:00:00Z"), ZoneOffset.UTC)

	@Test
	fun `service returns cached tide without calling provider`() {
		val cacheRepository = InMemoryTideCacheRepository().apply {
			save(availableCache())
		}
		val provider = FakeTideProvider()
		val service = service(provider, cacheRepository)

		val tide = service.getTideDay(spot, date)

		assertThat(tide.points).hasSize(1)
		assertThat(provider.fetchCount).isZero()
	}

	@Test
	fun `service returns missing api key without logging a provider call`() {
		val calls = InMemoryProviderCallLogRepository()
		val service = service(
			provider = FakeTideProvider(unavailableReason = TideUnavailableReason.MISSING_API_KEY),
			callRepository = calls,
		)

		val tide = service.getTideDay(spot, date)

		assertThat(tide.unavailableReason).isEqualTo(TideUnavailableReason.MISSING_API_KEY)
		assertThat(calls.calls).isEmpty()
	}

	@Test
	fun `service returns quota reached before starting a provider fetch`() {
		val calls = InMemoryProviderCallLogRepository(existingCalls = 5)
		val provider = FakeTideProvider(requiredCalls = 2)
		val service = service(provider = provider, callRepository = calls)

		val tide = service.getTideDay(spot, date)

		assertThat(tide.unavailableReason).isEqualTo(TideUnavailableReason.QUOTA_REACHED)
		assertThat(provider.fetchCount).isZero()
	}

	@Test
	fun `service fetches missing tide day stores it and logs provider calls`() {
		val cacheRepository = InMemoryTideCacheRepository()
		val calls = InMemoryProviderCallLogRepository()
		val provider = FakeTideProvider(requiredCalls = 2)
		val service = service(provider, cacheRepository, calls)

		val tide = service.getTideDay(spot, date)

		assertThat(tide.unavailableReason).isNull()
		assertThat(cacheRepository.findBySpotIdAndDateAndProvider(spot.id, date, provider.name)).isNotNull()
		assertThat(calls.calls).hasSize(2)
	}

	@Test
	fun `service logs prefetch purpose when preloading a missing tide day`() {
		val calls = InMemoryProviderCallLogRepository()
		val provider = FakeTideProvider(requiredCalls = 2)
		val service = service(provider = provider, callRepository = calls)

		service.prefetchTideDay(spot, date)

		assertThat(calls.calls).hasSize(2)
		assertThat(calls.calls).allSatisfy { call ->
			assertThat(call.purpose).isEqualTo(ProviderCallPurpose.TIDE_CACHE_PREFETCH)
		}
	}

	private fun service(
		provider: TideProvider = FakeTideProvider(),
		cacheRepository: InMemoryTideCacheRepository = InMemoryTideCacheRepository(),
		callRepository: InMemoryProviderCallLogRepository = InMemoryProviderCallLogRepository(),
	): TideService =
		TideService(
			tideProvider = provider,
			tideCacheRepository = cacheRepository,
			providerCallLogRepository = callRepository,
			properties = TideProperties(maxProviderCallsPerDay = 6),
			clock = clock,
		)

	private fun availableCache(): TideDayCache =
		TideDayCache(
			id = null,
			spotId = spot.id,
			date = date,
			providerName = "Stormglass",
			fetchedAt = clock.instant(),
			stationName = "Saint-Nazaire",
			stationDistanceKilometers = 12.4,
			coefficient = null,
			unavailableReason = null,
			unavailableMessage = null,
			points = listOf(TidePoint(Instant.parse("2026-06-04T10:00:00Z"), 3.2)),
			events = emptyList(),
		)

	private inner class FakeTideProvider(
		private val requiredCalls: Int = 2,
		private val unavailableReason: TideUnavailableReason? = null,
	) : TideProvider {
		override val name: String = "Stormglass"
		override val requiredCallsPerFetch: Int = requiredCalls
		var fetchCount: Int = 0

		override fun unavailableReason(): TideUnavailableReason? = unavailableReason

		override fun unavailableMessage(): String? =
			unavailableReason?.let { "Provider indisponible pour le test" }

		override fun fetchTideDay(spot: Spot, date: LocalDate): TideDayCache {
			fetchCount += 1
			return availableCache()
		}
	}
}

private class InMemoryTideCacheRepository : TideCacheRepository {
	private val caches = mutableMapOf<Triple<SpotId, LocalDate, String>, TideDayCache>()

	override fun save(cache: TideDayCache): TideDayCache {
		caches[Triple(cache.spotId, cache.date, cache.providerName)] = cache
		return cache
	}

	override fun findBySpotIdAndDateAndProvider(spotId: SpotId, date: LocalDate, providerName: String): TideDayCache? =
		caches[Triple(spotId, date, providerName)]

	override fun existsBySpotIdAndDateAndProvider(spotId: SpotId, date: LocalDate, providerName: String): Boolean =
		findBySpotIdAndDateAndProvider(spotId, date, providerName) != null
}

private class InMemoryProviderCallLogRepository(
	private val existingCalls: Int = 0,
) : ProviderCallLogRepository {
	val calls = mutableListOf<ProviderCallLog>()

	override fun save(call: ProviderCallLog): ProviderCallLog {
		calls += call
		return call
	}

	override fun countByProviderNameAndCalledAtBetween(providerName: String, startsAt: Instant, endsAt: Instant): Int =
		existingCalls + calls.count { it.providerName == providerName && it.calledAt >= startsAt && it.calledAt < endsAt }
}
