package ovh.tika.tikaswell.infrastructure.scheduling

import ovh.tika.tikaswell.application.spot.SpotProvider
import ovh.tika.tikaswell.application.tide.TideProperties
import ovh.tika.tikaswell.application.tide.TideService
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@Component
@ConditionalOnProperty(prefix = "tikaswell.tide.prefetch", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class TidePrefetchScheduler(
	private val tideService: TideService,
	private val spotProvider: SpotProvider,
	private val properties: TideProperties,
	private val clock: Clock,
) {
	@EventListener(ApplicationReadyEvent::class)
	fun prefetchShortWindowOnStartup() {
		prefetchWindow(properties.prefetch.startupDaysAhead, "démarrage")
	}

	@Scheduled(cron = "\${tikaswell.tide.prefetch.cron}", zone = "\${tikaswell.tide.prefetch.zone}")
	fun prefetchDailyWindow() {
		prefetchWindow(properties.prefetch.daysAhead, "planification quotidienne")
	}

	fun prefetchWindow(daysAhead: Int, trigger: String) {
		val spot = spotProvider.initialSpot()
		val today = LocalDate.now(clock.withZone(ZoneId.of(properties.prefetch.zone)))
		logger.info(
			"Préchargement marée {} pour le spot {} de {} à J+{}",
			trigger,
			spot.id.value,
			today,
			daysAhead,
		)

		for (offset in 0..daysAhead) {
			val date = today.plusDays(offset.toLong())
			val tide = tideService.prefetchTideDay(spot, date)
			if (tide.unavailableReason != null) {
				logger.info(
					"Marée indisponible pour {} ({}) : {}",
					date,
					tide.unavailableReason,
					tide.unavailableMessage ?: "pas de détail",
				)
				if (tide.unavailableReason.shouldStopPrefetchWindow()) {
					logger.info("Arrêt du préchargement marée {} après {}", trigger, date)
					break
				}
			}
		}
	}

	private companion object {
		private val logger = LoggerFactory.getLogger(TidePrefetchScheduler::class.java)
	}
}

private fun TideUnavailableReason.shouldStopPrefetchWindow(): Boolean =
	this == TideUnavailableReason.MISSING_API_KEY ||
		this == TideUnavailableReason.QUOTA_REACHED ||
		this == TideUnavailableReason.PROVIDER_UNAVAILABLE
