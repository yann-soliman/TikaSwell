package ovh.tika.tikaswell.application.scoring

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.CurrentConditions
import ovh.tika.tikaswell.domain.CurrentScore
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.ScoreContribution
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.SurfSessionId
import org.springframework.stereotype.Service
import java.time.Clock
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
	private val clock: Clock,
) {
	fun score(currentConditions: CurrentConditions): CurrentScore {
		val currentVector = ConditionVector.from(listOf(currentConditions.snapshot))
		val contributors = conditionSnapshotRepository.findBySpotId(currentConditions.spot.id)
			.groupBy { it.sessionId }
			.mapNotNull { (sessionId, snapshots) -> contributionFor(sessionId, snapshots, currentVector) }
			.filter { it.similarity > 0.0 }
			.sortedByDescending { it.similarity }
			.take(MAX_CONTRIBUTORS)

		if (contributors.isEmpty()) {
			return CurrentScore(
				score = null,
				confidence = 0.0,
				contributors = emptyList(),
				computedAt = clock.instant(),
			)
		}

		val similaritySum = contributors.sumOf { it.similarity }
		val weightedScore = contributors.sumOf { it.rating.value * it.similarity } / similaritySum
		val confidence = (contributors.size.toDouble() / MAX_CONTRIBUTORS) * contributors.map { it.similarity }.average()

		return CurrentScore(
			score = weightedScore.roundToSingleDecimal(),
			confidence = confidence.coerceIn(0.0, 1.0).roundToSingleDecimal(),
			contributors = contributors,
			computedAt = clock.instant(),
		)
	}

	private fun contributionFor(
		sessionId: SurfSessionId,
		snapshots: List<SessionConditionSnapshot>,
		currentVector: ConditionVector,
	): ScoreContribution? {
		val session = surfSessionRepository.findById(sessionId) ?: return null
		val historicalVector = ConditionVector.from(snapshots.map { it.snapshot })
		val similarity = currentVector.similarityTo(historicalVector)

		return ScoreContribution(
			sessionId = session.id!!,
			rating = session.rating,
			similarity = similarity.roundToSingleDecimal(),
		)
	}

	private companion object {
		const val MAX_CONTRIBUTORS = 5
	}
}

private data class ConditionVector(
	val timestamp: Instant,
	val windSpeedKmh: Double?,
	val windGustKmh: Double?,
	val windDirection: Direction?,
	val waveHeightMeters: Double?,
	val wavePeriodSeconds: Double?,
	val waveDirection: Direction?,
) {
	fun similarityTo(other: ConditionVector): Double {
		val differences = buildList {
			// Poids volontairement simples et lisibles : les vagues comptent plus que la rafale isolée.
			weightedDifference(windSpeedKmh, other.windSpeedKmh, scale = 40.0, weight = 1.4)?.let(::add)
			weightedDifference(windGustKmh, other.windGustKmh, scale = 60.0, weight = 0.6)?.let(::add)
			directionDifference(windDirection, other.windDirection, weight = 1.2)?.let(::add)
			weightedDifference(waveHeightMeters, other.waveHeightMeters, scale = 4.0, weight = 2.0)?.let(::add)
			weightedDifference(wavePeriodSeconds, other.wavePeriodSeconds, scale = 20.0, weight = 1.5)?.let(::add)
			directionDifference(waveDirection, other.waveDirection, weight = 1.0)?.let(::add)
		}

		if (differences.isEmpty()) {
			return 0.0
		}

		val weightedDistance = differences.sumOf { it.distance * it.weight } / differences.sumOf { it.weight }
		return (1.0 - weightedDistance).coerceIn(0.0, 1.0)
	}

	private data class WeightedDifference(
		val distance: Double,
		val weight: Double,
	)

	companion object {
		fun from(snapshots: List<ConditionSnapshot>): ConditionVector {
			require(snapshots.isNotEmpty()) { "Au moins un snapshot est nécessaire pour construire un vecteur" }
			return ConditionVector(
				timestamp = snapshots[snapshots.size / 2].timestamp,
				windSpeedKmh = snapshots.map { it.windSpeedKmh }.averageOrNull(),
				windGustKmh = snapshots.mapNotNull { it.windGustKmh }.averageOrNull(),
				windDirection = snapshots.mapNotNull { it.windDirection }.averageDirection(),
				waveHeightMeters = snapshots.mapNotNull { it.waveHeightMeters }.averageOrNull(),
				wavePeriodSeconds = snapshots.mapNotNull { it.wavePeriodSeconds }.averageOrNull(),
				waveDirection = snapshots.mapNotNull { it.waveDirection }.averageDirection(),
			)
		}

		private fun weightedDifference(left: Double?, right: Double?, scale: Double, weight: Double): WeightedDifference? {
			if (left == null || right == null) {
				return null
			}
			return WeightedDifference(
				distance = min(1.0, abs(left - right) / scale),
				weight = weight,
			)
		}

		private fun directionDifference(left: Direction?, right: Direction?, weight: Double): WeightedDifference? {
			if (left == null || right == null) {
				return null
			}
			val raw = abs(left.degrees - right.degrees)
			val shortest = min(raw, 360 - raw)
			return WeightedDifference(
				distance = shortest / 180.0,
				weight = weight,
			)
		}
	}
}

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
