package ovh.tika.tikaswell.infrastructure.stormglass

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("tikaswell.stormglass")
class StormglassProperties(
	val baseUrl: String,
	val apiKey: String,
) {
	val hasApiKey: Boolean
		get() = apiKey.isNotBlank()

	fun maskedApiKey(): String =
		if (hasApiKey) "<secret>" else "<not-configured>"

	override fun toString(): String =
		"StormglassProperties(baseUrl=$baseUrl, apiKey=${maskedApiKey()}, hasApiKey=$hasApiKey)"
}
