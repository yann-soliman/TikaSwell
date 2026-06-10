package ovh.tika.tikaswell.application.tide

import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TideEventType
import ovh.tika.tikaswell.domain.TidePhase
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideSnapshot
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

interface TideSnapshotLookup {
	fun snapshotAt(spot: Spot, instant: Instant): TideSnapshot?
}

@Service
class TideSnapshotService(
	private val tideService: TideService,
) : TideSnapshotLookup {
	private val zoneId = ZoneId.of("Europe/Paris")

	override fun snapshotAt(spot: Spot, instant: Instant): TideSnapshot? {
		val date = instant.atZone(zoneId).toLocalDate()
		val currentCache = tideService.getCachedTideDay(spot, date)
		if (currentCache.unavailableReason != null) {
			return null
		}

		// Les courbes api-maree.fr sont stockées par jour ; on lit les jours voisins pour éviter les trous à minuit.
		val caches = listOf(
			tideService.getCachedTideDay(spot, date.minusDays(1)),
			currentCache,
			tideService.getCachedTideDay(spot, date.plusDays(1)),
		).filter { it.unavailableReason == null }
		val points = caches.flatMap { it.points }
			.filter { it.waterHeightMeters != null }
			.distinctBy { it.timestamp }
			.sortedBy { it.timestamp }
		val events = caches.flatMap { it.events }.ifEmpty { points.deriveEvents() }
		val nearestPoint = points.nearestTo(instant)
		val previousHigh = events.previous(TideEventType.HIGH, instant)
		val previousLow = events.previous(TideEventType.LOW, instant)
		val nextHigh = events.next(TideEventType.HIGH, instant)
		val nextLow = events.next(TideEventType.LOW, instant)

		return TideSnapshot(
			spotId = spot.id,
			timestamp = instant,
			waterHeightMeters = nearestPoint?.waterHeightMeters,
			phase = phase(previousHigh, previousLow, nextHigh, nextLow),
			previousHighTide = previousHigh,
			previousLowTide = previousLow,
			nextHighTide = nextHigh,
			nextLowTide = nextLow,
			timeSincePreviousHighTide = previousHigh?.let { Duration.between(it.timestamp, instant) },
			timeSincePreviousLowTide = previousLow?.let { Duration.between(it.timestamp, instant) },
			timeUntilNextHighTide = nextHigh?.let { Duration.between(instant, it.timestamp) },
			timeUntilNextLowTide = nextLow?.let { Duration.between(instant, it.timestamp) },
			coefficient = currentCache.coefficient,
			providerName = currentCache.providerName,
		)
	}

	private fun phase(
		previousHigh: TideEvent?,
		previousLow: TideEvent?,
		nextHigh: TideEvent?,
		nextLow: TideEvent?,
	): TidePhase =
		when {
			previousLow != null && nextHigh != null && (previousHigh == null || previousLow.timestamp.isAfter(previousHigh.timestamp)) -> TidePhase.RISING
			previousHigh != null && nextLow != null && (previousLow == null || previousHigh.timestamp.isAfter(previousLow.timestamp)) -> TidePhase.FALLING
			nextHigh != null -> TidePhase.RISING
			nextLow != null -> TidePhase.FALLING
			else -> TidePhase.UNKNOWN
		}
}

private fun List<TidePoint>.nearestTo(instant: Instant): TidePoint? =
	filter { it.waterHeightMeters != null }
		.minByOrNull { abs(Duration.between(it.timestamp, instant).seconds) }

private fun List<TidePoint>.deriveEvents(): List<TideEvent> =
	windowed(3)
		.mapNotNull { (previous, current, next) ->
			val previousHeight = previous.waterHeightMeters ?: return@mapNotNull null
			val currentHeight = current.waterHeightMeters ?: return@mapNotNull null
			val nextHeight = next.waterHeightMeters ?: return@mapNotNull null
			when {
				currentHeight >= previousHeight && currentHeight >= nextHeight -> TideEvent(
					type = TideEventType.HIGH,
					timestamp = current.timestamp,
					waterHeightMeters = currentHeight,
				)
				currentHeight <= previousHeight && currentHeight <= nextHeight -> TideEvent(
					type = TideEventType.LOW,
					timestamp = current.timestamp,
					waterHeightMeters = currentHeight,
				)
				else -> null
			}
		}

private fun List<TideEvent>.previous(type: TideEventType, instant: Instant): TideEvent? =
	filter { it.type == type && !it.timestamp.isAfter(instant) }.maxByOrNull { it.timestamp }

private fun List<TideEvent>.next(type: TideEventType, instant: Instant): TideEvent? =
	filter { it.type == type && !it.timestamp.isBefore(instant) }.minByOrNull { it.timestamp }
