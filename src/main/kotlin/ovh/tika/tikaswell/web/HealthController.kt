package ovh.tika.tikaswell.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
	private val jdbcTemplate: JdbcTemplate,
) {
	@GetMapping("/health")
	fun health(): ResponseEntity<HealthResponse> {
		val databaseUp = runCatching { jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1 }
			.getOrDefault(false)
		val response = HealthResponse(
			status = if (databaseUp) "UP" else "DOWN",
			database = if (databaseUp) "UP" else "DOWN",
		)
		return ResponseEntity.status(if (databaseUp) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE)
			.body(response)
	}
}

data class HealthResponse(
	val status: String,
	val database: String,
)
