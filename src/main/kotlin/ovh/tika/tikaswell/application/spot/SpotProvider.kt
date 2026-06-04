package ovh.tika.tikaswell.application.spot

import ovh.tika.tikaswell.domain.Spot

interface SpotProvider {
	fun initialSpot(): Spot
}
