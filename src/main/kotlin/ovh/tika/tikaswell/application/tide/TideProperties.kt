package ovh.tika.tikaswell.application.tide

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("tikaswell.tide")
data class TideProperties(
	val maxProviderCallsPerDay: Int,
	val prefetch: TidePrefetchProperties = TidePrefetchProperties(),
) {
	init {
		require(maxProviderCallsPerDay >= 0) { "Le quota applicatif de marée doit être positif ou nul" }
	}
}

data class TidePrefetchProperties(
	val enabled: Boolean = true,
	val cron: String = "0 0 3 * * *",
	val zone: String = "Europe/Paris",
	val daysAhead: Int = 7,
	val startupDaysAhead: Int = 1,
) {
	init {
		require(daysAhead >= 0) { "La fenêtre de préchargement marée doit être positive ou nulle" }
		require(startupDaysAhead >= 0) { "La fenêtre de démarrage marée doit être positive ou nulle" }
		require(startupDaysAhead <= daysAhead) { "La fenêtre de démarrage marée ne doit pas dépasser la fenêtre quotidienne" }
		require(cron.isNotBlank()) { "La cron de préchargement marée est obligatoire" }
		require(zone.isNotBlank()) { "La zone horaire de préchargement marée est obligatoire" }
	}
}
