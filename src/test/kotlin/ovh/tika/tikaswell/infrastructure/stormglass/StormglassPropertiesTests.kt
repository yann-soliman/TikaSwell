package ovh.tika.tikaswell.infrastructure.stormglass

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StormglassPropertiesTests {
	@Test
	fun `properties hide configured api key from string representation`() {
		val properties = StormglassProperties(
			baseUrl = "https://api.stormglass.io",
			apiKey = "private-provider-key",
		)

		assertThat(properties.hasApiKey).isTrue()
		assertThat(properties.maskedApiKey()).isEqualTo("<secret>")
		assertThat(properties.toString()).doesNotContain("private-provider-key")
		assertThat(properties.toString()).contains("apiKey=<secret>")
	}

	@Test
	fun `properties expose missing api key without leaking anything`() {
		val properties = StormglassProperties(
			baseUrl = "https://api.stormglass.io",
			apiKey = "",
		)

		assertThat(properties.hasApiKey).isFalse()
		assertThat(properties.maskedApiKey()).isEqualTo("<not-configured>")
		assertThat(properties.toString()).contains("apiKey=<not-configured>")
	}
}
