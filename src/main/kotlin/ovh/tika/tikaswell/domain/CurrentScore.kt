package ovh.tika.tikaswell.domain

import java.time.Instant

data class CurrentScore(
	val score: Double?,
	val confidence: Double,
	val contributors: List<ScoreContribution>,
	val computedAt: Instant,
) {
	init {
		score?.let { require(it in 0.0..10.0) { "Score must be between 0 and 10" } }
		require(confidence in 0.0..1.0) { "Confidence must be between 0 and 1" }
	}
}

data class ScoreContribution(
	val sessionId: SurfSessionId,
	val rating: Rating,
	val similarity: Double,
) {
	init {
		require(similarity in 0.0..1.0) { "Similarity must be between 0 and 1" }
	}
}
