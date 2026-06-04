package ovh.tika.tikaswell.web

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTests {
	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	@BeforeEach
	fun cleanDatabase() {
		jdbcTemplate.update("DELETE FROM condition_snapshots")
		jdbcTemplate.update("DELETE FROM surf_sessions")
	}

	@Test
	fun `home page renders configured spot and empty session history`() {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Initial spot")))
			.andExpect(content().string(containsString("Ajouter une session")))
			.andExpect(content().string(containsString("Aucune session enregistrée pour le moment.")))
	}

	@Test
	fun `session form validates end time after start time`() {
		mockMvc.perform(
			post("/sessions")
				.param("date", "2026-06-04")
				.param("startTime", "10:00")
				.param("endTime", "09:00")
				.param("rating", "8")
				.param("notes", "Too short"),
		)
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("L&#39;heure de fin doit être après l&#39;heure de début")))
	}

	@Test
	fun `session form saves a valid session`() {
		mockMvc.perform(
			post("/sessions")
				.param("date", "2026-06-04")
				.param("startTime", "09:00")
				.param("endTime", "11:00")
				.param("rating", "8")
				.param("notes", "Clean morning lines"),
		)
			.andExpect(status().is3xxRedirection)
			.andExpect(redirectedUrl("/"))

		mockMvc.perform(get("/"))
			.andExpect(status().isOk)
			.andExpect(content().string(containsString("Clean morning lines")))
			.andExpect(content().string(containsString("8")))
	}
}
