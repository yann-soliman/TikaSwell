package ovh.tika.tikaswell.application.scoring

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.application.tide.TideSnapshotLookup
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.CurrentScore
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.ScoreContribution
import ovh.tika.tikaswell.domain.ScoreFactor
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.SurfSessionId
import ovh.tika.tikaswell.domain.TidePhase
import ovh.tika.tikaswell.domain.TideSnapshot
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Service
class SurfScoreService(
	private val conditionSnapshotRepository: ConditionSnapshotRepository,
	private val surfSessionRepository: SurfSessionRepository,
	private val tideSnapshotLookup: TideSnapshotLookup,
	private val clock: Clock,
) {
	fun score(currentConditions: CurrentConditions): CurrentScore {
		val currentTide = tideSnapshotLookup.snapshotAt(currentConditions.spot, currentConditions.snapshot.timestamp)
		val currentVector = ConditionVector.from(listOf(currentConditions.snapshot), currentTide)
		val contributors = conditionSnapshotRepository.findBySpotId(currentConditions.spot.id)
			.groupBy { it.sessionId }
			.mapNotNull { (sessionId, snapshots) -> contributionFor(sessionId, snapshots, currentConditions, currentVector) }
			.filter { it.contribution.similarity > 0.0 }
			.sortedByDescending { it.contribution.similarity }
			.take(MAX_CONTRIBUTORS)

		if (contributors.isEmpty()) {
			return CurrentScore(
				score = null,
				confidence = 0.0,
				contributors = emptyList(),
				computedAt = clock.instant(),
				tideUsed = false,
				factors = emptyList(),
			)
		}

		val similaritySum = contributors.sumOf { it.contribution.similarity }
		val weightedScore = contributors.sumOf { it.contribution.rating.value * it.contribution.similarity } / similaritySum
		val confidence = (contributors.size.toDouble() / MAX_CONTRIBUTORS) * contributors.map { it.contribution.similarity }.average()
		val factors = contributors
			.flatMap { it.factors }
			.groupBy { it.key }
			.map { (key, factorContributions) ->
				val weight = factorContributions.sumOf { it.weight }
				ScoreFactor(
					key = key,
					similarity = if (weight == 0.0) 0.0 else factorContributions.sumOf { it.similarity * it.weight } / weight,
					weight = weight,
					used = factorContributions.any { it.used },
				)
			}
			.sortedByDescending { it.weight }

		return CurrentScore(
			score = weightedScore.roundToSingleDecimal(),
			confidence = confidence.coerceIn(0.0, 1.0).roundToSingleDecimal(),
			contributors = contributors.map { it.contribution },
			computedAt = clock.instant(),
			tideUsed = contributors.any { it.contribution.tideUsed },
			factors = factors,
		)
	}

	private fun contributionFor(
		sessionId: SurfSessionId,
		snapshots: List<SessionConditionSnapshot>,
		currentConditions: CurrentConditions,
		currentVector: ConditionVector,
	): ContributionWithFactors? {
		val session = surfSessionRepository.findById(sessionId) ?: return null
		val historicalSnapshots = snapshots.map { it.snapshot }.sortedBy { it.timestamp }
		val historicalTimestamp = historicalSnapshots[historicalSnapshots.size / 2].timestamp
		val historicalTide = tideSnapshotLookup.snapshotAt(currentConditions.spot, historicalTimestamp)
		val historicalVector = ConditionVector.from(historicalSnapshots, historicalTide)
		val similarity = currentVector.similarityTo(historicalVector)

		return ContributionWithFactors(
			contribution = ScoreContribution(
				sessionId = session.id!!,
				rating = session.rating,
				similarity = similarity.value.roundToSingleDecimal(),
				tideUsed = similarity.tideUsed,
			),
			factors = similarity.differences.map {
				ScoreFactor(
					key = it.key,
					similarity = (1.0 - it.distance).coerceIn(0.0, 1.0),
					weight = it.weight,
					used = true,
				)
			},
		)
	}

	private companion object {
		const val MAX_CONTRIBUTORS = 5
	}
}

private data class ContributionWithFactors(
	val contribution: ScoreContribution,
	val factors: List<ScoreFactor>,
)

private data class ConditionVector(
	val timestamp: Instant,
	val windSpeedKmh: Double?,
	val windGustKmh: Double?,
	val windDirection: Direction?,
	val waveHeightMeters: Double?,
	val wavePeriodSeconds: Double?,
	val waveDirection: Direction?,
	val tide: TideVector?,
) {
	fun similarityTo(other: ConditionVector): SimilarityResult {
		val differences = buildList {
			// Poids volontairement simples et lisibles : les vagues comptent plus que la rafale isolée.
			weightedDifference("wind", windSpeedKmh, other.windSpeedKmh, scale = 40.0, weight = 1.4)?.let(::add)
			weightedDifference("wind", windGustKmh, other.windGustKmh, scale = 60.0, weight = 0.6)?.let(::add)
			directionDifference("wind", windDirection, other.windDirection, weight = 1.2)?.let(::add)
			weightedDifference("waveHeight", waveHeightMeters, other.waveHeightMeters, scale = 4.0, weight = 2.0)?.let(::add)
			weightedDifference("wavePeriod", wavePeriodSeconds, other.wavePeriodSeconds, scale = 20.0, weight = 1.5)?.let(::add)
			directionDifference("waveDirection", waveDirection, other.waveDirection, weight = 1.0)?.let(::add)
			tide?.differencesTo(other.tide)?.let(::addAll)
		}

		if (differences.isEmpty()) {
			return SimilarityResult(value = 0.0, tideUsed = false, differences = emptyList())
		}

		val weightedDistance = differences.sumOf { it.distance * it.weight } / differences.sumOf { it.weight }
		return SimilarityResult(
			value = (1.0 - weightedDistance).coerceIn(0.0, 1.0),
			tideUsed = differences.any { it.source == DifferenceSource.TIDE },
			differences = differences,
		)
	}

	data class SimilarityResult(
		val value: Double,
		val tideUsed: Boolean,
		val differences: List<WeightedDifference>,
	)

	companion object {
		fun from(snapshots: List<ConditionSnapshot>, tideSnapshot: TideSnapshot?): ConditionVector {
			require(snapshots.isNotEmpty()) { "Au moins un snapshot est nécessaire pour construire un vecteur" }
			val sortedSnapshots = snapshots.sortedBy { it.timestamp }
			return ConditionVector(
				timestamp = sortedSnapshots[sortedSnapshots.size / 2].timestamp,
				windSpeedKmh = sortedSnapshots.map { it.windSpeedKmh }.averageOrNull(),
				windGustKmh = sortedSnapshots.mapNotNull { it.windGustKmh }.averageOrNull(),
				windDirection = sortedSnapshots.mapNotNull { it.windDirection }.averageDirection(),
				waveHeightMeters = sortedSnapshots.mapNotNull { it.waveHeightMeters }.averageOrNull(),
				wavePeriodSeconds = sortedSnapshots.mapNotNull { it.wavePeriodSeconds }.averageOrNull(),
				waveDirection = sortedSnapshots.mapNotNull { it.waveDirection }.averageDirection(),
				tide = tideSnapshot?.let(TideVector::from),
			)
		}

		private fun weightedDifference(key: String, left: Double?, right: Double?, scale: Double, weight: Double): WeightedDifference? {
			if (left == null || right == null) {
				return null
			}
			return WeightedDifference(
				key = key,
				distance = min(1.0, abs(left - right) / scale),
				weight = weight,
				source = DifferenceSource.WEATHER,
			)
		}

		private fun directionDifference(key: String, left: Direction?, right: Direction?, weight: Double): WeightedDifference? {
			if (left == null || right == null) {
				return null
			}
			val raw = abs(left.degrees - right.degrees)
			val shortest = min(raw, 360 - raw)
			return WeightedDifference(
				key = key,
				distance = shortest / 180.0,
				weight = weight,
				source = DifferenceSource.WEATHER,
			)
		}
	}
}

private data class TideVector(
	val waterHeightMeters: Double?,
	val phase: TidePhase?,
	val minutesToNearestHighTide: Double?,
	val minutesToNearestLowTide: Double?,
) {
	fun differencesTo(other: TideVector?): List<WeightedDifference> {
		if (other == null) {
			return emptyList()
		}
		return buildList {
			weightedTideDifference(waterHeightMeters, other.waterHeightMeters, scale = 4.0, weight = 1.2)?.let(::add)
			phaseDifference(phase, other.phase, weight = 0.8)?.let(::add)
			weightedTideDifference(minutesToNearestHighTide, other.minutesToNearestHighTide, scale = 360.0, weight = 0.8)?.let(::add)
			weightedTideDifference(minutesToNearestLowTide, other.minutesToNearestLowTide, scale = 360.0, weight = 0.8)?.let(::add)
		}
	}

	companion object {
		fun from(snapshot: TideSnapshot): TideVector =
			TideVector(
				waterHeightMeters = snapshot.waterHeightMeters,
				phase = snapshot.phase,
				minutesToNearestHighTide = nearestMinutes(snapshot.timeSincePreviousHighTide, snapshot.timeUntilNextHighTide),
				minutesToNearestLowTide = nearestMinutes(snapshot.timeSincePreviousLowTide, snapshot.timeUntilNextLowTide),
			)
	}
}

private enum class DifferenceSource {
	WEATHER,
	TIDE,
}

private data class WeightedDifference(
	val key: String,
	val distance: Double,
	val weight: Double,
	val source: DifferenceSource,
)

private fun weightedTideDifference(left: Double?, right: Double?, scale: Double, weight: Double): WeightedDifference? {
	if (left == null || right == null) {
		return null
	}
	return WeightedDifference(
		key = "tide",
		distance = min(1.0, abs(left - right) / scale),
		weight = weight,
		source = DifferenceSource.TIDE,
	)
}

private fun phaseDifference(left: TidePhase?, right: TidePhase?, weight: Double): WeightedDifference? {
	if (left == null || right == null || left == TidePhase.UNKNOWN || right == TidePhase.UNKNOWN) {
		return null
	}
	val distance = if (left == right) 0.0 else 1.0
	return WeightedDifference(
		key = "tide",
		distance = distance,
		weight = weight,
		source = DifferenceSource.TIDE,
	)
}

private fun nearestMinutes(previous: Duration?, next: Duration?): Double? =
	listOfNotNull(previous, next)
		.minOrNull()
		?.toMinutes()
		?.toDouble()

private fun List<Double>.averageOrNull(): Double? =
	if (isEmpty()) null else average()

private fun List<Direction>.averageDirection(): Direction? =
	if (isEmpty()) {
		null
	} else {
		val radians = map { it.degrees * PI / 180.0 }
		val x = radians.sumOf(::cos)
		val y = radians.sumOf(::sin)
		Direction((atan2(y, x) * 180.0 / PI).roundToInt().floorMod(360))
	}

private fun Int.floorMod(mod: Int): Int =
	((this % mod) + mod) % mod

private fun Double.roundToSingleDecimal(): Double =
	(roundToIntPreservingTenths() / 10.0).coerceIn(0.0, 10.0)

private fun Double.roundToIntPreservingTenths(): Int =
	(this * 10).roundToInt()
