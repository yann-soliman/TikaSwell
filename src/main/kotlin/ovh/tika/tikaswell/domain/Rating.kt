package ovh.tika.tikaswell.domain

@JvmInline
value class Rating(val value: Int) {
	init {
		require(value in 0..10) { "La note doit être comprise entre 0 et 10" }
	}
}
