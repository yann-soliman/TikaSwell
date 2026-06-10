package ovh.tika.tikaswell.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import ovh.tika.tikaswell.application.session.CreateSurfSessionCommand
import ovh.tika.tikaswell.application.session.SurfSessionService
import ovh.tika.tikaswell.application.spot.SpotProvider
import ovh.tika.tikaswell.application.scoring.SurfScoreService
import ovh.tika.tikaswell.application.tide.TideService
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.CurrentScore
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.ScoreContribution
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TideEventType
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Controller
class HomeController(
	private val spotProvider: SpotProvider,
	private val surfSessionService: SurfSessionService,
	private val conditionsProvider: ConditionsProvider,
	private val surfScoreService: SurfScoreService,
	private val tideService: TideService,
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
		val currentTide = currentConditions.getOrNull()?.snapshot?.timestamp?.let { timestamp ->
			tideService.getCachedTideDay(spot, timestamp.atZone(zoneId).toLocalDate())
				.let { TideContextView.from(it, timestamp, zoneId) }
		}

		if (!model.containsAttribute("form")) {
			model.addAttribute("form", form)
		}
		model.addAttribute("spot", spot)
		model.addAttribute("sessions", sessions.map { session ->
			SurfSessionView.from(
				session = session,
				zoneId = zoneId,
				tide = tideService.getCachedTideDay(spot, session.midpoint().atZone(zoneId).toLocalDate())
					.let { TideContextView.from(it, session.midpoint(), zoneId) },
			)
		})
		model.addAttribute("currentConditions", currentConditions.getOrNull()?.snapshot?.let(CurrentConditionsView::from))
		model.addAttribute("conditionsError", currentConditions.exceptionOrNull()?.message)
		model.addAttribute("currentTide", currentTide)
		model.addAttribute("currentScore", currentScore?.let(CurrentScoreView::from))
		model.addAttribute("scoreContributors", scoreContributors(currentScore, sessions))
		model.addAttribute("scoreTideNote", scoreTideNote(currentTide))
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

	private fun scoreTideNote(currentTide: TideContextView?): String =
		if (currentTide?.available == true) {
			"La marée est affichée pour le contexte, mais elle n'influence pas encore la similarité."
		} else {
			"La marée est ignorée dans la similarité faute de données exploitables."
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
	val tide: TideContextView,
) {
	companion object {
		private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
		private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

		fun from(session: SurfSession, zoneId: ZoneId, tide: TideContextView): SurfSessionView {
			val startsAt = session.startsAt.atZone(zoneId)
			val endsAt = session.endsAt.atZone(zoneId)
			return SurfSessionView(
				id = session.id!!.value,
				date = startsAt.format(dateFormatter),
				timeWindow = "${startsAt.format(timeFormatter)} - ${endsAt.format(timeFormatter)}",
				rating = session.rating.value,
				notes = session.notes,
				tide = tide,
			)
		}
	}
}

data class TideContextView(
	val available: Boolean,
	val waterHeight: String,
	val phase: String,
	val nextHighTide: String,
	val nextLowTide: String,
	val providerName: String,
	val station: String?,
	val status: String,
) {
	companion object {
		private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

		fun from(cache: TideDayCache, instant: Instant, zoneId: ZoneId): TideContextView {
			if (cache.unavailableReason != null) {
				return unavailable(cache, messageFor(cache.unavailableReason, cache.unavailableMessage))
			}

			val point = cache.points.nearestTo(instant)
			val previousEvent = cache.events.filter { !it.timestamp.isAfter(instant) }.maxByOrNull { it.timestamp }
			val nextEvent = cache.events.filter { !it.timestamp.isBefore(instant) }.minByOrNull { it.timestamp }
			val station = stationLabel(cache.stationName, cache.stationDistanceKilometers)

			return TideContextView(
				available = true,
				waterHeight = point?.waterHeightMeters?.let { "${it.format(2)} m" } ?: "n/d",
				phase = phaseLabel(previousEvent, nextEvent),
				nextHighTide = cache.events.next(TideEventType.HIGH, instant)?.formatAt(zoneId) ?: "n/d",
				nextLowTide = cache.events.next(TideEventType.LOW, instant)?.formatAt(zoneId) ?: "n/d",
				providerName = cache.providerName,
				station = station,
				status = "Marée issue du cache local",
			)
		}

		private fun unavailable(cache: TideDayCache, message: String): TideContextView =
			TideContextView(
				available = false,
				waterHeight = "n/d",
				phase = "n/d",
				nextHighTide = "n/d",
				nextLowTide = "n/d",
				providerName = cache.providerName,
				station = null,
				status = message,
			)

		private fun messageFor(reason: TideUnavailableReason, providerMessage: String?): String =
			when (reason) {
				TideUnavailableReason.CACHE_MISS -> "Marée absente du cache pour cette date"
				TideUnavailableReason.MISSING_API_KEY -> "Clé API marée absente"
				TideUnavailableReason.QUOTA_REACHED -> "Quota marée atteint pour aujourd'hui"
				TideUnavailableReason.PROVIDER_UNAVAILABLE -> providerMessage ?: "Provider marée indisponible"
			}

		private fun phaseLabel(previousEvent: TideEvent?, nextEvent: TideEvent?): String =
			when {
				previousEvent?.type == TideEventType.LOW && nextEvent?.type == TideEventType.HIGH -> "Montante"
				previousEvent?.type == TideEventType.HIGH && nextEvent?.type == TideEventType.LOW -> "Descendante"
				previousEvent?.type == TideEventType.HIGH -> "Après pleine mer"
				previousEvent?.type == TideEventType.LOW -> "Après basse mer"
				nextEvent?.type == TideEventType.HIGH -> "Avant pleine mer"
				nextEvent?.type == TideEventType.LOW -> "Avant basse mer"
				else -> "n/d"
			}

		private fun TideEvent.formatAt(zoneId: ZoneId): String =
			timestamp.atZone(zoneId).format(timeFormatter)

		private fun stationLabel(stationName: String?, stationDistanceKilometers: Double?): String? {
			val meaningfulName = stationName?.trim()?.takeUnless { it.equals("station", ignoreCase = true) }
			return when {
				meaningfulName != null && stationDistanceKilometers != null -> "$meaningfulName · ${stationDistanceKilometers.format(1)} km"
				meaningfulName != null -> meaningfulName
				stationDistanceKilometers != null -> "Station à ${stationDistanceKilometers.format(1)} km"
				else -> null
			}
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

private fun SurfSession.midpoint(): Instant =
	startsAt.plus(Duration.between(startsAt, endsAt).dividedBy(2))

private fun List<TidePoint>.nearestTo(instant: Instant): TidePoint? =
	minByOrNull { abs(Duration.between(it.timestamp, instant).seconds) }

private fun List<TideEvent>.next(type: TideEventType, instant: Instant): TideEvent? =
	filter { it.type == type && !it.timestamp.isBefore(instant) }.minByOrNull { it.timestamp }
