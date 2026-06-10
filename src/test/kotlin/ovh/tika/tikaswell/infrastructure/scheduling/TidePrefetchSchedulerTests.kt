package ovh.tika.tikaswell.infrastructure.scheduling

import ovh.tika.tikaswell.application.spot.SpotProvider
import ovh.tika.tikaswell.application.tide.ProviderCallLogRepository
import ovh.tika.tikaswell.application.tide.TideCacheRepository
import ovh.tika.tikaswell.application.tide.TidePrefetchProperties
import ovh.tika.tikaswell.application.tide.TideProperties
import ovh.tika.tikaswell.application.tide.TideProvider
import ovh.tika.tikaswell.application.tide.TideService
import ovh.tika.tikaswell.domain.ProviderCallLog
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TidePoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TidePrefetchSchedulerTests {
	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)
	private val clock = Clock.fixed(Instant.parse("2026-06-04T01:30:00Z"), ZoneOffset.UTC)

	@Test
	fun `scheduler prefetches every date from today to configured horizon`() {
		val provider = RecordingTideProvider()
		val scheduler = scheduler(provider)

		scheduler.prefetchWindow(daysAhead = 2, trigger = "test")

		assertThat(provider.fetchedDates).containsExactly(
			LocalDate.parse("2026-06-04"),
			LocalDate.parse("2026-06-05"),
			LocalDate.parse("2026-06-06"),
		)
	}

	@Test
	fun `scheduler relies on tide service cache-first behavior`() {
		val provider = RecordingTideProvider()
		val cacheRepository = InMemoryTideCacheRepository().apply {
			save(cache(LocalDate.parse("2026-06-05")))
		}
		val scheduler = scheduler(provider, cacheRepository)

		scheduler.prefetchWindow(daysAhead = 2, trigger = "test")

		assertThat(provider.fetchedDates).containsExactly(
			LocalDate.parse("2026-06-04"),
			LocalDate.parse("2026-06-06"),
		)
	}

	@Test
	fun `scheduler stops current window when provider is unavailable`() {
		val provider = RecordingTideProvider(failure = IllegalStateException("403 Forbidden"))
		val scheduler = scheduler(provider)

		scheduler.prefetchWindow(daysAhead = 2, trigger = "test")

		assertThat(provider.fetchedDates).containsExactly(LocalDate.parse("2026-06-04"))
	}

	private fun scheduler(
		provider: RecordingTideProvider,
		cacheRepository: InMemoryTideCacheRepository = InMemoryTideCacheRepository(),
	): TidePrefetchScheduler =
		TidePrefetchScheduler(
			tideService = TideService(
				tideProvider = provider,
				tideCacheRepository = cacheRepository,
				providerCallLogRepository = InMemoryProviderCallLogRepository(),
				properties = TideProperties(
					maxProviderCallsPerDay = 6,
					prefetch = TidePrefetchProperties(
						daysAhead = 7,
						startupDaysAhead = 1,
						zone = "Europe/Paris",
					),
				),
				clock = clock,
			),
			spotProvider = object : SpotProvider {
				override fun initialSpot(): Spot = spot
			},
			properties = TideProperties(
				maxProviderCallsPerDay = 6,
				prefetch = TidePrefetchProperties(
					daysAhead = 7,
					startupDaysAhead = 1,
					zone = "Europe/Paris",
				),
			),
			clock = clock,
		)

	private fun cache(date: LocalDate): TideDayCache =
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
			points = listOf(TidePoint(date.atStartOfDay().toInstant(ZoneOffset.UTC), 3.2)),
			events = emptyList(),
		)

	private inner class RecordingTideProvider(
		private val failure: RuntimeException? = null,
	) : TideProvider {
		override val name: String = "Stormglass"
		override val requiredCallsPerFetch: Int = 2
		val fetchedDates = mutableListOf<LocalDate>()

		override fun fetchTideDay(spot: Spot, date: LocalDate): TideDayCache {
			fetchedDates += date
			failure?.let { throw it }
			return cache(date)
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

private class InMemoryProviderCallLogRepository : ProviderCallLogRepository {
	override fun save(call: ProviderCallLog): ProviderCallLog = call

	override fun countByProviderNameAndCalledAtBetween(providerName: String, startsAt: Instant, endsAt: Instant): Int = 0
}
