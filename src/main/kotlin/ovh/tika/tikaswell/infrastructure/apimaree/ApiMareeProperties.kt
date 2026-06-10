package ovh.tika.tikaswell.infrastructure.apimaree

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("tikaswell.api-maree")
class ApiMareeProperties(
	val baseUrl: String,
	val apiKey: String,
	val siteId: String,
	val stepMinutes: Int,
	val timezone: String,
) {
	val hasApiKey: Boolean
		get() = apiKey.isNotBlank()

	fun maskedApiKey(): String =
		if (hasApiKey) "<secret>" else "<not-configured>"

	override fun toString(): String =
		"ApiMareeProperties(baseUrl=$baseUrl, apiKey=${maskedApiKey()}, siteId=$siteId, stepMinutes=$stepMinutes, timezone=$timezone)"
}
