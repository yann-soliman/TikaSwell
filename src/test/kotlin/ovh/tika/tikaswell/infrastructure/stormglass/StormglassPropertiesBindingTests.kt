package ovh.tika.tikaswell.infrastructure.stormglass

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class StormglassPropertiesBindingTests {
	@Autowired
	private lateinit var properties: StormglassProperties

	@Test
	fun `stormglass properties are bound without requiring an api key`() {
		assertThat(properties.baseUrl).isEqualTo("http://localhost/stormglass-test")
		assertThat(properties.hasApiKey).isFalse()
		assertThat(properties.toString()).contains("apiKey=<not-configured>")
	}
}
