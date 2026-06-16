package ovh.tika.tikaswell.web

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.application.session.SurfSessionService
import ovh.tika.tikaswell.application.spot.SpotProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Controller
class ExportController(
	private val spotProvider: SpotProvider,
	private val surfSessionService: SurfSessionService,
	private val conditionSnapshotRepository: ConditionSnapshotRepository,
) {
	private val zoneId = ZoneId.of("Europe/Paris")
	private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
	private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

	@GetMapping("/exports/sessions.csv")
	fun exportSessionsCsv(): ResponseEntity<String> {
		val spot = spotProvider.initialSpot()
		val rows = surfSessionService.listForInitialSpot(spot.id).map { session ->
			val startsAt = session.startsAt.atZone(zoneId)
			val endsAt = session.endsAt.atZone(zoneId)
			val conditions = session.id?.let { conditionSnapshotRepository.findBySessionId(it) }.orEmpty()
				.let(SessionConditionsView::from)
			listOf(
				session.id?.value?.toString().orEmpty(),
				spot.id.value,
				spot.name,
				startsAt.format(dateFormatter),
				startsAt.format(timeFormatter),
				endsAt.format(timeFormatter),
				session.rating.value.toString(),
				session.notes.orEmpty(),
				conditions.providerName.orEmpty(),
				conditions.summary,
			)
		}
		val csv = buildString {
			appendLine(CSV_HEADER.joinToString(",") { it.csvCell() })
			rows.forEach { row -> appendLine(row.joinToString(",") { it.csvCell() }) }
		}

		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tikaswell-sessions.csv\"")
			.contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
			.body(csv)
	}

	private companion object {
		val CSV_HEADER = listOf(
			"id",
			"spot_id",
			"spot_name",
			"date",
			"starts_at",
			"ends_at",
			"rating",
			"notes",
			"conditions_provider",
			"conditions_summary",
		)
	}
}

private fun String.csvCell(): String =
	"\"" + replace("\"", "\"\"") + "\""
