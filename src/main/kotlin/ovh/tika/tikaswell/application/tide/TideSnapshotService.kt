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
		val cache = tideService.getCachedTideDay(spot, instant.atZone(zoneId).toLocalDate())
		if (cache.unavailableReason != null) {
			return null
		}

		val nearestPoint = cache.points.nearestTo(instant)
		val previousHigh = cache.events.previous(TideEventType.HIGH, instant)
		val previousLow = cache.events.previous(TideEventType.LOW, instant)
		val nextHigh = cache.events.next(TideEventType.HIGH, instant)
		val nextLow = cache.events.next(TideEventType.LOW, instant)

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
			coefficient = cache.coefficient,
			providerName = cache.providerName,
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

private fun List<TideEvent>.previous(type: TideEventType, instant: Instant): TideEvent? =
	filter { it.type == type && !it.timestamp.isAfter(instant) }.maxByOrNull { it.timestamp }

private fun List<TideEvent>.next(type: TideEventType, instant: Instant): TideEvent? =
	filter { it.type == type && !it.timestamp.isBefore(instant) }.minByOrNull { it.timestamp }
