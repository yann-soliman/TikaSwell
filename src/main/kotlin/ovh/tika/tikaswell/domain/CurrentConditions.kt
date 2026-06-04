package ovh.tika.tikaswell.domain

import java.time.Instant

data class CurrentConditions(
	val spot: Spot,
	val fetchedAt: Instant,
	val snapshot: ConditionSnapshot,
) {
	init {
		require(snapshot.spotId == spot.id) { "Le snapshot de conditions doit appartenir au spot" }
	}
}
