package ovh.tika.tikaswell.domain

@JvmInline
value class Direction(val degrees: Int) {
	init {
		require(degrees in 0..359) { "La direction doit être comprise entre 0 et 359 degrés" }
	}
}
