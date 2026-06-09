package ovh.tika.tikaswell.application.tide

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("tikaswell.tide")
data class TideProperties(
	val maxProviderCallsPerDay: Int,
) {
	init {
		require(maxProviderCallsPerDay >= 0) { "Le quota applicatif de marée doit être positif ou nul" }
	}
}
