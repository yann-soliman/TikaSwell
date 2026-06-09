package ovh.tika.tikaswell.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import ovh.tika.tikaswell.application.session.CreateSurfSessionCommand
import ovh.tika.tikaswell.application.session.SurfSessionService
import ovh.tika.tikaswell.application.spot.SpotProvider
import ovh.tika.tikaswell.application.scoring.SurfScoreService
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.CurrentScore
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.ScoreContribution
import ovh.tika.tikaswell.domain.SurfSession
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Controller
class HomeController(
	private val spotProvider: SpotProvider,
	private val surfSessionService: SurfSessionService,
	private val conditionsProvider: ConditionsProvider,
	private val surfScoreService: SurfScoreService,
) {
	private val zoneId = ZoneId.of("Europe/Paris")

	@GetMapping("/")
	fun home(model: Model): String {
		populateHomeModel(model, SurfSessionForm.default())
		return "home"
	}

	@PostMapping("/sessions")
	fun createSession(
		@Valid @ModelAttribute("form") form: SurfSessionForm,
		bindingResult: BindingResult,
		model: Model,
		redirectAttributes: RedirectAttributes,
	): String {
		if (form.startTime != null && form.endTime != null && !form.startTime.isBefore(form.endTime)) {
			bindingResult.rejectValue("endTime", "session.endTime.afterStart", "L'heure de fin doit être après l'heure de début")
		}

		if (bindingResult.hasErrors()) {
			populateHomeModel(model, form)
			return "home"
		}

		val spot = spotProvider.initialSpot()
		val startsAt = form.date!!.atTime(form.startTime).atZone(zoneId).toInstant()
		val endsAt = form.date.atTime(form.endTime).atZone(zoneId).toInstant()

		surfSessionService.create(
			CreateSurfSessionCommand(
				spot = spot,
				startsAt = startsAt,
				endsAt = endsAt,
				rating = Rating(form.rating!!),
				notes = form.notes,
			),
		)

		redirectAttributes.addFlashAttribute("message", "Session enregistrée")
		return "redirect:/"
	}

	private fun populateHomeModel(model: Model, form: SurfSessionForm) {
		val spot = spotProvider.initialSpot()
		val sessions = surfSessionService.listForInitialSpot(spot.id)
		val currentConditions = runCatching { conditionsProvider.fetchCurrentConditions(spot) }
		val currentScore = currentConditions.getOrNull()?.let { surfScoreService.score(it) }

		if (!model.containsAttribute("form")) {
			model.addAttribute("form", form)
		}
		model.addAttribute("spot", spot)
		model.addAttribute("sessions", sessions.map { SurfSessionView.from(it, zoneId) })
		model.addAttribute("currentConditions", currentConditions.getOrNull()?.snapshot?.let(CurrentConditionsView::from))
		model.addAttribute("conditionsError", currentConditions.exceptionOrNull()?.message)
		model.addAttribute("currentScore", currentScore?.let(CurrentScoreView::from))
		model.addAttribute("scoreContributors", scoreContributors(currentScore, sessions))
	}

	private fun scoreContributors(score: CurrentScore?, sessions: List<SurfSession>): List<ScoreContributionView> {
		if (score == null) {
			return emptyList()
		}
		val sessionsById = sessions.mapNotNull { session -> session.id?.let { it to session } }.toMap()
		return score.contributors.mapNotNull { contribution ->
			sessionsById[contribution.sessionId]?.let { session ->
				ScoreContributionView.from(contribution, session, zoneId)
			}
		}
	}
}

data class SurfSessionForm(
	@field:NotNull(message = "La date est obligatoire")
	val date: LocalDate?,
	@field:NotNull(message = "L'heure de début est obligatoire")
	val startTime: LocalTime?,
	@field:NotNull(message = "L'heure de fin est obligatoire")
	val endTime: LocalTime?,
	@field:NotNull(message = "La note est obligatoire")
	@field:Min(value = 0, message = "La note doit être au moins 0")
	@field:Max(value = 10, message = "La note doit être au plus 10")
	val rating: Int?,
	val notes: String?,
) {
	companion object {
		fun default(): SurfSessionForm = SurfSessionForm(
			date = LocalDate.now(),
			startTime = null,
			endTime = null,
			rating = null,
			notes = null,
		)
	}
}

data class SurfSessionView(
	val id: Long,
	val date: String,
	val timeWindow: String,
	val rating: Int,
	val notes: String?,
) {
	companion object {
		private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
		private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

		fun from(session: SurfSession, zoneId: ZoneId): SurfSessionView {
			val startsAt = session.startsAt.atZone(zoneId)
			val endsAt = session.endsAt.atZone(zoneId)
			return SurfSessionView(
				id = session.id!!.value,
				date = startsAt.format(dateFormatter),
				timeWindow = "${startsAt.format(timeFormatter)} - ${endsAt.format(timeFormatter)}",
				rating = session.rating.value,
				notes = session.notes,
			)
		}
	}
}

data class CurrentConditionsView(
	val windSpeed: String,
	val windGust: String,
	val windDirection: String,
	val waveHeight: String,
	val wavePeriod: String,
	val wavePeakPeriod: String,
	val waveDirection: String,
	val swellWaveHeight: String,
	val swellWavePeriod: String,
	val swellWavePeakPeriod: String,
	val swellWaveDirection: String,
	val windWaveHeight: String,
	val windWavePeriod: String,
	val windWavePeakPeriod: String,
	val windWaveDirection: String,
	val providerName: String,
	val observedAt: String,
) {
	companion object {
		private val observedAtFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")

		fun from(snapshot: ConditionSnapshot): CurrentConditionsView {
			val observedAt = snapshot.timestamp.atZone(ZoneId.of("Europe/Paris")).format(observedAtFormatter)
			return CurrentConditionsView(
				windSpeed = "${snapshot.windSpeedKmh.format(1)} km/h",
				windGust = snapshot.windGustKmh?.let { "${it.format(1)} km/h" } ?: "n/d",
				windDirection = snapshot.windDirection?.let { "${it.degrees}°" } ?: "n/d",
				waveHeight = snapshot.waveHeightMeters?.let { "${it.format(1)} m" } ?: "n/d",
				wavePeriod = snapshot.wavePeriodSeconds?.let { "${it.format(1)} s" } ?: "n/d",
				wavePeakPeriod = snapshot.wavePeakPeriodSeconds?.let { "${it.format(1)} s" } ?: "n/d",
				waveDirection = snapshot.waveDirection?.let { "${it.degrees}°" } ?: "n/d",
				swellWaveHeight = snapshot.swellWaveHeightMeters?.let { "${it.format(1)} m" } ?: "n/d",
				swellWavePeriod = snapshot.swellWavePeriodSeconds?.let { "${it.format(1)} s" } ?: "n/d",
				swellWavePeakPeriod = snapshot.swellWavePeakPeriodSeconds?.let { "${it.format(1)} s" } ?: "n/d",
				swellWaveDirection = snapshot.swellWaveDirection?.let { "${it.degrees}°" } ?: "n/d",
				windWaveHeight = snapshot.windWaveHeightMeters?.let { "${it.format(1)} m" } ?: "n/d",
				windWavePeriod = snapshot.windWavePeriodSeconds?.let { "${it.format(1)} s" } ?: "n/d",
				windWavePeakPeriod = snapshot.windWavePeakPeriodSeconds?.let { "${it.format(1)} s" } ?: "n/d",
				windWaveDirection = snapshot.windWaveDirection?.let { "${it.degrees}°" } ?: "n/d",
				providerName = snapshot.providerName,
				observedAt = observedAt,
			)
		}
	}
}

data class CurrentScoreView(
	val score: String,
	val confidence: String,
	val hasScore: Boolean,
) {
	companion object {
		fun from(score: CurrentScore): CurrentScoreView =
			CurrentScoreView(
				score = score.score?.format(1) ?: "Pas assez d'historique",
				confidence = "${(score.confidence * 100).format(0)} %",
				hasScore = score.score != null,
			)
	}
}

data class ScoreContributionView(
	val date: String,
	val timeWindow: String,
	val rating: Int,
	val similarity: String,
) {
	companion object {
		private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
		private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

		fun from(contribution: ScoreContribution, session: SurfSession, zoneId: ZoneId): ScoreContributionView {
			val startsAt = session.startsAt.atZone(zoneId)
			val endsAt = session.endsAt.atZone(zoneId)
			return ScoreContributionView(
				date = startsAt.format(dateFormatter),
				timeWindow = "${startsAt.format(timeFormatter)} - ${endsAt.format(timeFormatter)}",
				rating = contribution.rating.value,
				similarity = "${(contribution.similarity * 100).format(0)} %",
			)
		}
	}
}

private fun Double.format(decimals: Int): String =
	"%.${decimals}f".format(java.util.Locale.FRANCE, this)
