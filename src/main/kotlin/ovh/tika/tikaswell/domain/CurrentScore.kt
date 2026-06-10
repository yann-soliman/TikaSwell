package ovh.tika.tikaswell.domain

import java.time.Instant

data class CurrentScore(
	val score: Double?,
	val confidence: Double,
	val contributors: List<ScoreContribution>,
	val computedAt: Instant,
	val tideUsed: Boolean,
) {
	init {
		score?.let { require(it in 0.0..10.0) { "Le score doit être compris entre 0 et 10" } }
		require(confidence in 0.0..1.0) { "La confiance doit être comprise entre 0 et 1" }
	}
}

data class ScoreContribution(
	val sessionId: SurfSessionId,
	val rating: Rating,
	val similarity: Double,
	val tideUsed: Boolean = false,
) {
	init {
		require(similarity in 0.0..1.0) { "La similarité doit être comprise entre 0 et 1" }
	}
}
