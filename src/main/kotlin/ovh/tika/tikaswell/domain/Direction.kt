package ovh.tika.tikaswell.domain

@JvmInline
value class Direction(val degrees: Int) {
	init {
		require(degrees in 0..359) { "Direction must be between 0 and 359 degrees" }
	}
}
