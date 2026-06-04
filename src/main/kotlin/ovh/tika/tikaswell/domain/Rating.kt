package ovh.tika.tikaswell.domain

@JvmInline
value class Rating(val value: Int) {
	init {
		require(value in 0..10) { "Rating must be between 0 and 10" }
	}
}
