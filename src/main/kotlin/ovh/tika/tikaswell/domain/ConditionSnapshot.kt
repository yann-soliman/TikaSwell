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
		require(windSpeedKmh >= 0.0) { "La vitesse du vent doit être positive ou nulle" }
		windGustKmh?.let { require(it >= 0.0) { "La rafale doit être positive ou nulle" } }
		waveHeightMeters?.let { require(it >= 0.0) { "La hauteur de vague doit être positive ou nulle" } }
		wavePeriodSeconds?.let { require(it >= 0.0) { "La période de vague doit être positive ou nulle" } }
		require(providerName.isNotBlank()) { "Le nom du fournisseur est obligatoire" }
	}
}
