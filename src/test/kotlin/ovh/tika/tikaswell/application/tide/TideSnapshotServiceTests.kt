package ovh.tika.tikaswell.application.tide

import ovh.tika.tikaswell.domain.ProviderCallLog
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TideEventType
import ovh.tika.tikaswell.domain.TidePhase
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TideSnapshotServiceTests {
	private val spot = Spot(
		id = SpotId("ermitage"),
		name = "Plage de l'Ermitage",
		latitude = 47.20744,
		longitude = -2.15987,
	)
	private val clock = Clock.fixed(Instant.parse("2026-06-04T10:00:00Z"), ZoneOffset.UTC)

	@Test
	fun `snapshot is calculated from cached tide points and events`() {
		val cacheRepository = SnapshotInMemoryTideCacheRepository().apply {
			save(availableCache())
		}
		val service = TideSnapshotService(tideService(cacheRepository))

		val snapshot = service.snapshotAt(spot, Instant.parse("2026-06-04T10:00:00Z"))

		assertThat(snapshot).isNotNull()
		assertThat(snapshot!!.waterHeightMeters).isEqualTo(3.2)
		assertThat(snapshot.phase).isEqualTo(TidePhase.RISING)
		assertThat(snapshot.previousLowTide?.timestamp).isEqualTo(Instant.parse("2026-06-04T06:20:00Z"))
		assertThat(snapshot.nextHighTide?.timestamp).isEqualTo(Instant.parse("2026-06-04T12:40:00Z"))
		assertThat(snapshot.timeSincePreviousLowTide?.toMinutes()).isEqualTo(220)
		assertThat(snapshot.timeUntilNextHighTide?.toMinutes()).isEqualTo(160)
	}

	@Test
	fun `snapshot is absent when tide cache is unavailable`() {
		val cacheRepository = SnapshotInMemoryTideCacheRepository().apply {
			save(availableCache().copy(unavailableReason = TideUnavailableReason.CACHE_MISS, unavailableMessage = "absent"))
		}
		val service = TideSnapshotService(tideService(cacheRepository))

		val snapshot = service.snapshotAt(spot, Instant.parse("2026-06-04T10:00:00Z"))

		assertThat(snapshot).isNull()
	}

	private fun tideService(cacheRepository: SnapshotInMemoryTideCacheRepository): TideService =
		TideService(
			tideProvider = FakeTideProvider(),
			tideCacheRepository = cacheRepository,
			providerCallLogRepository = SnapshotInMemoryProviderCallLogRepository(),
			properties = TideProperties(maxProviderCallsPerDay = 6),
			clock = clock,
		)

	private fun availableCache(): TideDayCache =
		TideDayCache(
			id = null,
			spotId = spot.id,
			date = LocalDate.parse("2026-06-04"),
			providerName = "Stormglass",
			fetchedAt = clock.instant(),
			stationName = "Saint-Nazaire",
			stationDistanceKilometers = 12.4,
			coefficient = null,
			unavailableReason = null,
			unavailableMessage = null,
			points = listOf(
				TidePoint(Instant.parse("2026-06-04T09:00:00Z"), 2.9),
				TidePoint(Instant.parse("2026-06-04T10:00:00Z"), 3.2),
			),
			events = listOf(
				TideEvent(TideEventType.LOW, Instant.parse("2026-06-04T06:20:00Z"), 1.1),
				TideEvent(TideEventType.HIGH, Instant.parse("2026-06-04T12:40:00Z"), 4.8),
				TideEvent(TideEventType.LOW, Instant.parse("2026-06-04T18:55:00Z"), 1.2),
			),
		)

	private class FakeTideProvider : TideProvider {
		override val name: String = "Stormglass"
		override val requiredCallsPerFetch: Int = 2

		override fun fetchTideDay(spot: Spot, date: LocalDate): TideDayCache =
			error("Le calcul de snapshot doit lire uniquement le cache")
	}
}

private class SnapshotInMemoryTideCacheRepository : TideCacheRepository {
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

private class SnapshotInMemoryProviderCallLogRepository : ProviderCallLogRepository {
	override fun save(call: ProviderCallLog): ProviderCallLog = call

	override fun countByProviderNameAndCalledAtBetween(providerName: String, startsAt: Instant, endsAt: Instant): Int = 0
}
