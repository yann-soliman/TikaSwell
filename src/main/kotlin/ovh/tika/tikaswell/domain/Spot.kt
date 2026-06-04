package ovh.tika.tikaswell.domain

@JvmInline
value class SpotId(val value: String) {
	init {
		require(value.isNotBlank()) { "Spot id is required" }
	}
}

data class Spot(
	val id: SpotId,
	val name: String,
	val latitude: Double,
	val longitude: Double,
) {
	init {
		require(name.isNotBlank()) { "Spot name is required" }
		require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
		require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
	}
}
