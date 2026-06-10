package ovh.tika.tikaswell.infrastructure.apimaree

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiMareePropertiesTests {
	@Test
	fun `properties hide configured api key from string representation`() {
		val properties = ApiMareeProperties(
			baseUrl = "https://api-maree.fr",
			apiKey = "private-provider-key",
			siteId = "saint-nazaire",
			stepMinutes = 10,
			timezone = "Europe/Paris",
		)

		assertThat(properties.hasApiKey).isTrue()
		assertThat(properties.maskedApiKey()).isEqualTo("<secret>")
		assertThat(properties.toString()).doesNotContain("private-provider-key")
		assertThat(properties.toString()).contains("apiKey=<secret>")
	}

	@Test
	fun `properties expose missing api key without leaking anything`() {
		val properties = ApiMareeProperties(
			baseUrl = "https://api-maree.fr",
			apiKey = "",
			siteId = "saint-nazaire",
			stepMinutes = 10,
			timezone = "Europe/Paris",
		)

		assertThat(properties.hasApiKey).isFalse()
		assertThat(properties.maskedApiKey()).isEqualTo("<not-configured>")
		assertThat(properties.toString()).contains("apiKey=<not-configured>")
	}
}
