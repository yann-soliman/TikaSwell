package ovh.tika.tikaswell.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.application.session.CreateSurfSessionCommand
import ovh.tika.tikaswell.application.session.SurfSessionService
import ovh.tika.tikaswell.application.spot.SpotProvider
import ovh.tika.tikaswell.application.scoring.SurfScoreService
import ovh.tika.tikaswell.application.tide.TideSnapshotLookup
import ovh.tika.tikaswell.application.tide.TideService
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.ConditionsProvider
import ovh.tika.tikaswell.domain.CurrentScore
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.ScoreContribution
import ovh.tika.tikaswell.domain.ScoreFactor
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.Spot
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TidePhase
import ovh.tika.tikaswell.domain.TideSnapshot
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Controller
class HomeController(
	private val spotProvider: SpotProvider,
	private val surfSessionService: SurfSessionService,
	private val conditionsProvider: ConditionsProvider,
	private val surfScoreService: SurfScoreService,
	private val tideService: TideService,
	private val tideSnapshotLookup: TideSnapshotLookup,
	private val conditionSnapshotRepository: ConditionSnapshotRepository,
) {
	private val zoneId = ZoneId.of("Europe/Paris")

	@GetMapping("/")
	fun home(
		@RequestParam(name = "saved", required = false) saved: String?,
		@RequestParam(name = "refreshed", required = false) refreshed: String?,
		@RequestParam(name = "refreshError", required = false) refreshError: String?,
		@RequestParam(name = "ui", required = false, defaultValue = "v1") uiVersion: String,
		model: Model,
	): String {
		addStatusMessage(model, saved, refreshed, refreshError)
		populateHomeModel(model, SurfSessionForm.default(), uiVersion.normalizedUiVersion())
		return uiVersion.normalizedTemplate()
	}

	@GetMapping("/v2")
	fun homeV2(
		@RequestParam(name = "saved", required = false) saved: String?,
		@RequestParam(name = "refreshed", required = false) refreshed: String?,
		@RequestParam(name = "refreshError", required = false) refreshError: String?,
		model: Model,
	): String {
		addStatusMessage(model, saved, refreshed, refreshError)
		populateHomeModel(model, SurfSessionForm.default(), "v2")
		return "home-v2"
	}

	@GetMapping("/v3")
	fun homeV3(
		@RequestParam(name = "saved", required = false) saved: String?,
		@RequestParam(name = "refreshed", required = false) refreshed: String?,
		@RequestParam(name = "refreshError", required = false) refreshError: String?,
		model: Model,
	): String {
		addStatusMessage(model, saved, refreshed, refreshError)
		populateHomeModel(model, SurfSessionForm.default(), "v3")
		return "home-v3"
	}

	@PostMapping("/conditions/refresh")
	fun refreshConditions(
		@RequestParam(name = "uiVersion", required = false, defaultValue = "v1") uiVersion: String,
	): String {
		val normalizedUiVersion = uiVersion.normalizedUiVersion()
		val spot = spotProvider.initialSpot()
		val refreshed = runCatching { conditionsProvider.fetchCurrentConditions(spot) }.isSuccess
		val flag = if (refreshed) "refreshed=1" else "refreshError=1"
		return when (normalizedUiVersion) {
			"v2" -> "redirect:/v2?$flag"
			"v3" -> "redirect:/v3?$flag"
			else -> "redirect:/?$flag"
		}
	}

	@PostMapping("/sessions")
	fun createSession(
		@Valid @ModelAttribute("form") form: SurfSessionForm,
		bindingResult: BindingResult,
		@RequestParam(name = "uiVersion", required = false, defaultValue = "v1") uiVersion: String,
		model: Model,
	): String {
		val normalizedUiVersion = uiVersion.normalizedUiVersion()
		if (form.startTime != null && form.endTime != null && !form.startTime.isBefore(form.endTime)) {
			bindingResult.rejectValue("endTime", "session.endTime.afterStart", "L'heure de fin doit être après l'heure de début")
		}

		if (bindingResult.hasErrors()) {
			populateHomeModel(model, form, normalizedUiVersion)
			return normalizedUiVersion.templateName()
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

		return when (normalizedUiVersion) {
			"v2" -> "redirect:/v2?saved=1"
			"v3" -> "redirect:/v3?saved=1"
			else -> "redirect:/?saved=1"
		}
	}

	private fun populateHomeModel(model: Model, form: SurfSessionForm, uiVersion: String) {
		val spot = spotProvider.initialSpot()
		val sessions = surfSessionService.listForInitialSpot(spot.id)
		val currentConditions = runCatching { conditionsProvider.fetchCurrentConditions(spot) }
		val currentScore = currentConditions.getOrNull()?.let { surfScoreService.score(it) }
		val currentTide = currentConditions.getOrNull()?.snapshot?.timestamp?.let { timestamp ->
			val cache = tideService.getCachedTideDay(spot, timestamp.atZone(zoneId).toLocalDate())
			TideContextView.from(cache, tideSnapshotLookup.snapshotAt(spot, timestamp), zoneId)
		}

		if (!model.containsAttribute("form")) {
			model.addAttribute("form", form)
		}
		model.addAttribute("spot", spot)
		model.addAttribute("sessions", sessions.map { session ->
			SurfSessionView.from(
				session = session,
				zoneId = zoneId,
				tide = tideContext(spot, session.midpoint()),
				conditions = session.id?.let { conditionSnapshotRepository.findBySessionId(it) }.orEmpty()
					.let(SessionConditionsView::from),
			)
		})
		model.addAttribute("currentConditions", currentConditions.getOrNull()?.snapshot?.let(CurrentConditionsView::from))
		model.addAttribute("conditionsError", currentConditions.exceptionOrNull()?.message)
		model.addAttribute("currentTide", currentTide)
		val scoreContributors = scoreContributors(currentScore, sessions)
		model.addAttribute("currentScore", currentScore?.let(CurrentScoreView::from))
		model.addAttribute("scoreContributors", scoreContributors)
		model.addAttribute("scoreTideNote", scoreTideNote(currentScore, currentTide))
		model.addAttribute("sessionStats", SessionStatsView.from(sessions))
		model.addAttribute("v3Verdict", V3VerdictView.from(currentScore?.let(CurrentScoreView::from), scoreContributors.size, currentConditions.isFailure))
		model.addAttribute("v3ScoreFactors", v3ScoreFactors(currentScore, currentConditions.getOrNull()?.snapshot, currentTide))
		model.addAttribute("uiVersion", uiVersion)
	}

	private fun addStatusMessage(model: Model, saved: String?, refreshed: String?, refreshError: String?) {
		when {
			saved == "1" -> model.addAttribute("message", "Session enregistrée")
			refreshed == "1" -> model.addAttribute("message", "Conditions rafraîchies")
			refreshError == "1" -> model.addAttribute("message", "Rafraîchissement impossible")
		}
	}

	private fun tideContext(spot: Spot, instant: Instant): TideContextView {
		val cache = tideService.getCachedTideDay(spot, instant.atZone(zoneId).toLocalDate())
		return TideContextView.from(cache, tideSnapshotLookup.snapshotAt(spot, instant), zoneId)
	}

	private fun scoreContributors(score: CurrentScore?, sessions: List<SurfSession>): List<ScoreContributionView> {
		if (score == null) {
			return emptyList()
		}
		val sessionsById = sessions.mapNotNull { session -> session.id?.let { it to session } }.toMap()
		return score.contributors.mapNotNull { contribution ->
			sessionsById[contribution.sessionId]?.let { session ->
				ScoreContributionView.from(
					contribution = contribution,
					session = session,
					zoneId = zoneId,
					conditions = session.id?.let { conditionSnapshotRepository.findBySessionId(it) }.orEmpty()
						.let(SessionConditionsView::from),
				)
			}
		}
	}

	private fun scoreTideNote(currentScore: CurrentScore?, currentTide: TideContextView?): String =
		when {
			currentScore?.tideUsed == true -> "La marée influence la similarité : hauteur d'eau, phase et distance aux pleine/basse mers sont comparées."
			currentTide?.available == true -> "La marée est disponible, mais ignorée faute de données historiques comparables."
			else -> "La marée est ignorée dans la similarité faute de données exploitables."
		}

	private fun v3ScoreFactors(
		currentScore: CurrentScore?,
		currentSnapshot: ConditionSnapshot?,
		currentTide: TideContextView?,
	): List<V3ScoreFactorView> {
		val factorsByKey = currentScore?.factors.orEmpty().associateBy { it.key }
		return listOf(
			V3ScoreFactorView.from(
				title = "Hauteur",
				value = currentSnapshot?.waveHeightMeters?.let { "${it.format(1)} m" },
				factor = factorsByKey["waveHeight"],
				unavailableLabel = "Vagues n/d",
			),
			V3ScoreFactorView.from(
				title = "Période",
				value = currentSnapshot?.wavePeriodSeconds?.let { "${it.format(1)} s" },
				factor = factorsByKey["wavePeriod"],
				unavailableLabel = "Période n/d",
			),
			V3ScoreFactorView.from(
				title = "Direction",
				value = currentSnapshot?.swellWaveDirection?.let { "${it.degrees}°" }
					?: currentSnapshot?.waveDirection?.let { "${it.degrees}°" },
				factor = factorsByKey["waveDirection"],
				unavailableLabel = "Direction n/d",
			),
			V3ScoreFactorView.from(
				title = "Vent",
				value = currentSnapshot?.windSpeedKmh?.let { "${it.format(1)} km/h" },
				factor = factorsByKey["wind"],
				unavailableLabel = "Vent n/d",
			),
			V3ScoreFactorView.from(
				title = "Marée",
				value = currentTide?.takeIf { it.available }?.let { "${it.phase} · ${it.waterHeight}" },
				factor = factorsByKey["tide"],
				unavailableLabel = currentTide?.status ?: "Marée n/d",
			),
		)
	}

	private fun String.normalizedTemplate(): String =
		normalizedUiVersion().templateName()

	private fun String.normalizedUiVersion(): String =
		when {
			equals("v2", ignoreCase = true) -> "v2"
			equals("v3", ignoreCase = true) -> "v3"
			else -> "v1"
		}

	private fun String.templateName(): String =
		when (this) {
			"v2" -> "home-v2"
			"v3" -> "home-v3"
			else -> "home"
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
	val conditions: SessionConditionsView,
) {
	companion object {
		private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
		private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

		fun from(
			session: SurfSession,
			zoneId: ZoneId,
			tide: TideContextView,
			conditions: SessionConditionsView,
		): SurfSessionView {
			val startsAt = session.startsAt.atZone(zoneId)
			val endsAt = session.endsAt.atZone(zoneId)
			return SurfSessionView(
				id = session.id!!.value,
				date = startsAt.format(dateFormatter),
				timeWindow = "${startsAt.format(timeFormatter)} - ${endsAt.format(timeFormatter)}",
				rating = session.rating.value,
				notes = session.notes,
				tide = tide,
				conditions = conditions,
			)
		}
	}
}

data class SessionConditionsView(
	val captured: Boolean,
	val summary: String,
	val providerName: String?,
) {
	companion object {
		fun from(snapshots: List<SessionConditionSnapshot>): SessionConditionsView {
			if (snapshots.isEmpty()) {
				return SessionConditionsView(
					captured = false,
					summary = "Conditions météo/marine non capturées",
					providerName = null,
				)
			}

			val conditions = snapshots.map { it.snapshot }
			val providerName = conditions.map { it.providerName }.distinct().singleOrNull() ?: "Sources multiples"
			val windSpeed = conditions.map { it.windSpeedKmh }.averageOrNull()?.let { "${it.format(1)} km/h" } ?: "n/d"
			val windGust = conditions.mapNotNull { it.windGustKmh }.averageOrNull()?.let { "${it.format(1)} km/h" } ?: "n/d"
			val windDirection = conditions.mapNotNull { it.windDirection }.averageDirection()?.let { "${it.degrees}°" } ?: "n/d"
			val waveHeight = conditions.mapNotNull { it.waveHeightMeters }.averageOrNull()?.let { "${it.format(1)} m" } ?: "n/d"
			val wavePeriod = conditions.mapNotNull { it.wavePeriodSeconds }.averageOrNull()?.let { "${it.format(1)} s" } ?: "n/d"
			val waveDirection = conditions.mapNotNull { it.waveDirection }.averageDirection()?.let { "${it.degrees}°" } ?: "n/d"

			return SessionConditionsView(
				captured = true,
				summary = "Vent $windSpeed, rafales $windGust, dir. $windDirection · Vagues $waveHeight, période $wavePeriod, dir. $waveDirection",
				providerName = providerName,
			)
		}
	}
}

data class TideContextView(
	val available: Boolean,
	val waterHeight: String,
	val phase: String,
	val previousHighTide: String,
	val previousLowTide: String,
	val nextHighTide: String,
	val nextLowTide: String,
	val providerName: String,
	val station: String?,
	val status: String,
) {
	companion object {
		private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

		fun from(cache: TideDayCache, snapshot: TideSnapshot?, zoneId: ZoneId): TideContextView {
			if (cache.unavailableReason != null) {
				return unavailable(cache, messageFor(cache.unavailableReason, cache.unavailableMessage))
			}

			val station = stationLabel(cache.stationName, cache.stationDistanceKilometers)

			return TideContextView(
				available = true,
				waterHeight = snapshot?.waterHeightMeters?.let { "${it.format(2)} m" } ?: "n/d",
				phase = snapshot?.phase?.label() ?: "n/d",
				previousHighTide = snapshot?.previousHighTide?.formatAt(zoneId) ?: "n/d",
				previousLowTide = snapshot?.previousLowTide?.formatAt(zoneId) ?: "n/d",
				nextHighTide = snapshot?.nextHighTide?.formatAt(zoneId) ?: "n/d",
				nextLowTide = snapshot?.nextLowTide?.formatAt(zoneId) ?: "n/d",
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
				previousHighTide = "n/d",
				previousLowTide = "n/d",
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

		private fun TidePhase.label(): String =
			when {
				this == TidePhase.RISING -> "Montante"
				this == TidePhase.FALLING -> "Descendante"
				this == TidePhase.HIGH -> "Pleine mer"
				this == TidePhase.LOW -> "Basse mer"
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
	val observedAtCompact: String,
) {
	companion object {
		private val observedAtFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")
		private val observedAtCompactFormatter = DateTimeFormatter.ofPattern("dd MMM · HH:mm", java.util.Locale.FRANCE)

		fun from(snapshot: ConditionSnapshot): CurrentConditionsView {
			val observedAtDateTime = snapshot.timestamp.atZone(ZoneId.of("Europe/Paris"))
			val observedAt = observedAtDateTime.format(observedAtFormatter)
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
				observedAtCompact = observedAtDateTime.format(observedAtCompactFormatter),
			)
		}
	}
}

data class CurrentScoreView(
	val score: String,
	val scorePercent: Int,
	val interpretation: String,
	val summary: String,
	val confidence: String,
	val hasScore: Boolean,
	val tideUsed: Boolean,
) {
	companion object {
		fun from(score: CurrentScore): CurrentScoreView {
			val numericScore = score.score
			val contributorCount = score.contributors.size
			return CurrentScoreView(
				score = numericScore?.format(1) ?: "Pas assez d'historique",
				scorePercent = numericScore?.let { ((it / 10.0) * 100).roundToInt().coerceIn(0, 100) } ?: 0,
				interpretation = numericScore.interpretation(),
				summary = numericScore.summary(contributorCount),
				confidence = "${(score.confidence * 100).format(0)} %",
				hasScore = numericScore != null,
				tideUsed = score.tideUsed,
			)
		}

		private fun Double?.interpretation(): String =
			when {
				this == null -> "Score indisponible"
				this >= 8.0 -> "Très prometteur"
				this >= 6.5 -> "Prometteur"
				this >= 5.0 -> "À surveiller"
				else -> "Peu engageant"
			}

		private fun Double?.summary(contributorCount: Int): String =
			if (this == null || contributorCount == 0) {
				"Pas encore assez de sessions historiques comparables pour estimer le spot."
			} else {
				"Conditions proches de $contributorCount session(s) passée(s). Le score reste empirique et dépend de ton historique."
			}
	}
}

data class ScoreContributionView(
	val date: String,
	val timeWindow: String,
	val rating: Int,
	val similarity: String,
	val similarityPercent: Int,
	val conditions: String,
	val notes: String,
) {
	companion object {
		private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
		private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

		fun from(
			contribution: ScoreContribution,
			session: SurfSession,
			zoneId: ZoneId,
			conditions: SessionConditionsView,
		): ScoreContributionView {
			val startsAt = session.startsAt.atZone(zoneId)
			val endsAt = session.endsAt.atZone(zoneId)
			return ScoreContributionView(
				date = startsAt.format(dateFormatter),
				timeWindow = "${startsAt.format(timeFormatter)} - ${endsAt.format(timeFormatter)}",
				rating = contribution.rating.value,
				similarity = "${(contribution.similarity * 100).format(0)} %",
				similarityPercent = (contribution.similarity * 100).roundToInt().coerceIn(0, 100),
				conditions = conditions.summary,
				notes = session.notes?.takeIf { it.isNotBlank() } ?: "—",
			)
		}
	}
}

data class SessionStatsView(
	val count: Int,
	val averageRating: String,
	val bestRating: String,
	val chartBars: List<Int>,
) {
	companion object {
		fun from(sessions: List<SurfSession>): SessionStatsView {
			if (sessions.isEmpty()) {
				return SessionStatsView(
					count = 0,
					averageRating = "n/d",
					bestRating = "n/d",
					chartBars = emptyList(),
				)
			}

			val ratings = sessions.map { it.rating.value }
			val recentBars = sessions
				.sortedBy { it.startsAt }
				.takeLast(7)
				.map { ((it.rating.value / 10.0) * 100).roundToInt().coerceIn(8, 100) }

			return SessionStatsView(
				count = sessions.size,
				averageRating = ratings.average().format(1),
				bestRating = "${ratings.maxOrNull()}/10",
				chartBars = recentBars,
			)
		}
	}
}

data class V3VerdictView(
	val title: String,
	val summary: String,
	val score: String,
	val scorePercent: Int,
	val confidence: String,
	val hasScore: Boolean,
	val hasProviderError: Boolean,
	val momentLabel: String,
	val referenceLabel: String,
) {
	companion object {
		fun from(score: CurrentScoreView?, contributorCount: Int, hasProviderError: Boolean): V3VerdictView {
			if (hasProviderError) {
				return V3VerdictView(
					title = "Indisponible",
					summary = "Le fournisseur de données ne répond pas. Les sessions restent consultables.",
					score = "n/d",
					scorePercent = 0,
					confidence = "0 %",
					hasScore = false,
					hasProviderError = true,
					momentLabel = "n/d",
					referenceLabel = "n/d",
				)
			}
			if (score == null || !score.hasScore) {
				return V3VerdictView(
					title = "Incertain",
					summary = "Pas encore assez de sessions historiques similaires pour estimer le spot.",
					score = "n/d",
					scorePercent = 0,
					confidence = score?.confidence ?: "0 %",
					hasScore = false,
					hasProviderError = false,
					momentLabel = "À confirmer",
					referenceLabel = "0 session",
				)
			}

			return V3VerdictView(
				title = score.interpretation,
				summary = score.summary,
				score = score.score,
				scorePercent = score.scorePercent,
				confidence = score.confidence,
				hasScore = true,
				hasProviderError = false,
				momentLabel = "Maintenant",
				referenceLabel = "$contributorCount session(s)",
			)
		}
	}
}

data class V3ScoreFactorView(
	val title: String,
	val value: String,
	val label: String,
	val level: Int,
	val toneClass: String,
) {
	companion object {
		fun from(title: String, value: String?, factor: ScoreFactor?, unavailableLabel: String): V3ScoreFactorView {
			if (factor == null || !factor.used) {
				return V3ScoreFactorView(
					title = title,
					value = value ?: unavailableLabel,
					label = "Non comparé",
					level = 0,
					toneClass = "factor--neutral",
				)
			}

			val level = (factor.similarity * 100).roundToInt().coerceIn(0, 100)
			val label = when {
				level >= 85 -> "Très proche"
				level >= 70 -> "Proche"
				level >= 50 -> "Écart modéré"
				else -> "Écart fort"
			}
			val toneClass = when {
				level >= 70 -> ""
				level >= 50 -> "factor--warn"
				else -> "factor--neutral"
			}
			return V3ScoreFactorView(
				title = title,
				value = value ?: unavailableLabel,
				label = label,
				level = level,
				toneClass = toneClass,
			)
		}
	}
}

private fun Double.format(decimals: Int): String =
	"%.${decimals}f".format(java.util.Locale.FRANCE, this)

private fun List<Double>.averageOrNull(): Double? =
	if (isEmpty()) null else average()

private fun List<Direction>.averageDirection(): Direction? =
	if (isEmpty()) {
		null
	} else {
		val radians = map { it.degrees * PI / 180.0 }
		val x = radians.sumOf(::cos)
		val y = radians.sumOf(::sin)
		Direction((atan2(y, x) * 180.0 / PI).roundToInt().floorMod(360))
	}

private fun Int.floorMod(mod: Int): Int =
	((this % mod) + mod) % mod

private fun SurfSession.midpoint(): Instant =
	startsAt.plus(Duration.between(startsAt, endsAt).dividedBy(2))
