package ovh.tika.tikaswell.application.conditions

import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.TimeWindow
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.ZoneId

@ConfigurationProperties("tikaswell.conditions.backfill")
data class HistoricalConditionsBackfillProperties(
	val enabled: Boolean = true,
	val daysBefore: Int = 30,
	val cron: String = "0 30 3 * * *",
	val zone: String = "Europe/Paris",
) {
	init {
		require(daysBefore >= 0) { "La fenêtre de backfill conditions doit être positive ou nulle" }
		require(cron.isNotBlank()) { "La cron du backfill conditions est obligatoire" }
		require(zone.isNotBlank()) { "La zone horaire du backfill conditions est obligatoire" }
	}
}

@Service
class HistoricalConditionsBackfillService(
	private val surfSessionRepository: SurfSessionRepository,
	private val conditionSnapshotRepository: ConditionSnapshotRepository,
	private val conditionsProvider: ConditionsProvider,
	private val properties: HistoricalConditionsBackfillProperties,
	private val clock: Clock,
) {
	fun backfillMissingSessionConditions(spot: Spot): BackfillConditionsResult {
		if (!properties.enabled) {
			return BackfillConditionsResult(scannedSessions = 0, backfilledSessions = 0, failedSessions = 0)
		}

		val zoneId = ZoneId.of(properties.zone)
		val today = clock.instant().atZone(zoneId).toLocalDate()
		val earliestDate = today.minusDays(properties.daysBefore.toLong())
		val sessions = surfSessionRepository.findBySpotId(spot.id)
			.filter { session ->
				val sessionDate = session.startsAt.atZone(zoneId).toLocalDate()
				sessionDate in earliestDate..<today
			}

		var backfilled = 0
		var failed = 0
		sessions.forEach { session ->
			val sessionId = session.id ?: return@forEach
			if (conditionSnapshotRepository.findBySessionId(sessionId).isNotEmpty()) {
				return@forEach
			}

			try {
				val snapshots = conditionsProvider.fetchHistoricalConditions(
					spot = spot,
					window = TimeWindow(session.startsAt, session.endsAt),
				)
				snapshots.forEach { snapshot ->
					conditionSnapshotRepository.saveForSession(sessionId, snapshot)
				}
				backfilled += 1
			} catch (exception: Exception) {
				failed += 1
				logger.warn("Backfill conditions impossible pour la session {}", sessionId.value, exception)
			}
		}

		return BackfillConditionsResult(
			scannedSessions = sessions.size,
			backfilledSessions = backfilled,
			failedSessions = failed,
		)
	}

	private companion object {
		private val logger = LoggerFactory.getLogger(HistoricalConditionsBackfillService::class.java)
	}
}

data class BackfillConditionsResult(
	val scannedSessions: Int,
	val backfilledSessions: Int,
	val failedSessions: Int,
)
