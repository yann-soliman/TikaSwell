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
	val wavePeakPeriodSeconds: Double? = null,
	val waveDirection: Direction?,
	val windWaveHeightMeters: Double? = null,
	val windWavePeriodSeconds: Double? = null,
	val windWavePeakPeriodSeconds: Double? = null,
	val windWaveDirection: Direction? = null,
	val swellWaveHeightMeters: Double? = null,
	val swellWavePeriodSeconds: Double? = null,
	val swellWavePeakPeriodSeconds: Double? = null,
	val swellWaveDirection: Direction? = null,
	val providerName: String,
) {
	init {
		require(windSpeedKmh >= 0.0) { "La vitesse du vent doit être positive ou nulle" }
		windGustKmh?.let { require(it >= 0.0) { "La rafale doit être positive ou nulle" } }
		waveHeightMeters?.let { require(it >= 0.0) { "La hauteur de vague doit être positive ou nulle" } }
		wavePeriodSeconds?.let { require(it >= 0.0) { "La période de vague doit être positive ou nulle" } }
		wavePeakPeriodSeconds?.let { require(it >= 0.0) { "La période pic de vague doit être positive ou nulle" } }
		windWaveHeightMeters?.let { require(it >= 0.0) { "La hauteur de mer du vent doit être positive ou nulle" } }
		windWavePeriodSeconds?.let { require(it >= 0.0) { "La période de mer du vent doit être positive ou nulle" } }
		windWavePeakPeriodSeconds?.let { require(it >= 0.0) { "La période pic de mer du vent doit être positive ou nulle" } }
		swellWaveHeightMeters?.let { require(it >= 0.0) { "La hauteur de houle doit être positive ou nulle" } }
		swellWavePeriodSeconds?.let { require(it >= 0.0) { "La période de houle doit être positive ou nulle" } }
		swellWavePeakPeriodSeconds?.let { require(it >= 0.0) { "La période pic de houle doit être positive ou nulle" } }
		require(providerName.isNotBlank()) { "Le nom du fournisseur est obligatoire" }
	}
}
