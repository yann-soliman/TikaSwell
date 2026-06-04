package ovh.tika.tikaswell.infrastructure.config

import ovh.tika.tikaswell.application.spot.SpotProvider
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SpotId
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties("tikaswell.spot")
data class SpotProperties(
	val id: String,
	val name: String,
	val latitude: Double,
	val longitude: Double,
) {
	fun toSpot(): Spot = Spot(
		id = SpotId(id),
		name = name,
		latitude = latitude,
		longitude = longitude,
	)
}

@Component
class ConfiguredSpotProvider(
	private val properties: SpotProperties,
) : SpotProvider {
	override fun initialSpot(): Spot = properties.toSpot()
}
