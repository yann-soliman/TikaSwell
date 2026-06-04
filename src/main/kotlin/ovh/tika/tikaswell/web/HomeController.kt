package ovh.tika.tikaswell.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import ovh.tika.tikaswell.application.session.CreateSurfSessionCommand
import ovh.tika.tikaswell.application.session.SurfSessionService
import ovh.tika.tikaswell.application.spot.SpotProvider
import ovh.tika.tikaswell.domain.Rating
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
				spotId = spot.id,
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

		if (!model.containsAttribute("form")) {
			model.addAttribute("form", form)
		}
		model.addAttribute("spot", spot)
		model.addAttribute("sessions", sessions.map { SurfSessionView.from(it, zoneId) })
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
