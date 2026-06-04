package ovh.tika.tikaswell.domain

@JvmInline
value class SpotId(val value: String) {
	init {
		require(value.isNotBlank()) { "L'id du spot est obligatoire" }
	}
}

data class Spot(
	val id: SpotId,
	val name: String,
	val latitude: Double,
	val longitude: Double,
) {
	init {
		require(name.isNotBlank()) { "Le nom du spot est obligatoire" }
		require(latitude in -90.0..90.0) { "La latitude doit être comprise entre -90 et 90" }
		require(longitude in -180.0..180.0) { "La longitude doit être comprise entre -180 et 180" }
	}
}
