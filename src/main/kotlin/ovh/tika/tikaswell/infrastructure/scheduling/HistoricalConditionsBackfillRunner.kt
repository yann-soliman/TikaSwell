package ovh.tika.tikaswell.infrastructure.scheduling

import ovh.tika.tikaswell.application.conditions.HistoricalConditionsBackfillService
import ovh.tika.tikaswell.application.spot.SpotProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class HistoricalConditionsBackfillRunner(
	private val spotProvider: SpotProvider,
	private val backfillService: HistoricalConditionsBackfillService,
) {
	@EventListener(ApplicationReadyEvent::class)
	fun backfillOnStartup() {
		val spot = spotProvider.initialSpot()
		val result = backfillService.backfillMissingSessionConditions(spot)
		logger.info(
			"Backfill conditions démarrage pour le spot {} : {} session(s) scannée(s), {} complétée(s), {} en échec",
			spot.id.value,
			result.scannedSessions,
			result.backfilledSessions,
			result.failedSessions,
		)
	}

	private companion object {
		private val logger = LoggerFactory.getLogger(HistoricalConditionsBackfillRunner::class.java)
	}
}
