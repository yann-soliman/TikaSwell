package ovh.tika.tikaswell.domain

import java.time.Instant

data class ConditionSnapshot(
	val spotId: SpotId,
	val timestamp: Instant,
	val windSpeedKmh: Double,
	val windGustKmh: Double?,
	val windDirection: Direction?,
	val waveHeightMeters: Double?,
	val wavePeriodSeconds: Double?,
	val waveDirection: Direction?,
	val providerName: String,
) {
	init {
		require(windSpeedKmh >= 0.0) { "Wind speed must be positive or zero" }
		windGustKmh?.let { require(it >= 0.0) { "Wind gust must be positive or zero" } }
		waveHeightMeters?.let { require(it >= 0.0) { "Wave height must be positive or zero" } }
		wavePeriodSeconds?.let { require(it >= 0.0) { "Wave period must be positive or zero" } }
		require(providerName.isNotBlank()) { "Provider name is required" }
	}
}
