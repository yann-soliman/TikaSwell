package ovh.tika.tikaswell.domain

interface ConditionsProvider {
	val name: String

	fun fetchCurrentConditions(spot: Spot): CurrentConditions

	fun fetchHistoricalConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot>

	fun fetchForecastConditions(spot: Spot, window: TimeWindow): List<ConditionSnapshot>
}
