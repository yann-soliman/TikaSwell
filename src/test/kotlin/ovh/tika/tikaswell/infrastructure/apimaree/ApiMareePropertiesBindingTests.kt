package ovh.tika.tikaswell.infrastructure.apimaree

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ApiMareePropertiesBindingTests {
	@Autowired
	private lateinit var properties: ApiMareeProperties

	@Test
	fun `api maree properties are bound without requiring an api key`() {
		assertThat(properties.baseUrl).isEqualTo("http://localhost/api-maree-test")
		assertThat(properties.siteId).isEqualTo("saint-nazaire")
		assertThat(properties.stepMinutes).isEqualTo(10)
		assertThat(properties.timezone).isEqualTo("Europe/Paris")
		assertThat(properties.hasApiKey).isFalse()
		assertThat(properties.toString()).contains("apiKey=<not-configured>")
	}
}
