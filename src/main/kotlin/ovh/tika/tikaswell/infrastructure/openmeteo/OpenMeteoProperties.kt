package ovh.tika.tikaswell.infrastructure.openmeteo

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("tikaswell.open-meteo")
data class OpenMeteoProperties(
	val weatherBaseUrl: String,
	val marineBaseUrl: String,
)
