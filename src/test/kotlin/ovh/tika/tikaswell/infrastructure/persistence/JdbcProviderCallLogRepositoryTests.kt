package ovh.tika.tikaswell.infrastructure.persistence

import ovh.tika.tikaswell.application.tide.ProviderCallLogRepository
import ovh.tika.tikaswell.domain.ProviderCallLog
import ovh.tika.tikaswell.domain.ProviderCallPurpose
import ovh.tika.tikaswell.domain.ProviderCallResult
import ovh.tika.tikaswell.domain.SpotId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
class JdbcProviderCallLogRepositoryTests {
	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	@Autowired
	private lateinit var repository: ProviderCallLogRepository

	@BeforeEach
	fun cleanDatabase() {
		jdbcTemplate.update("DELETE FROM provider_call_log")
	}

	@Test
	fun `provider call log stores calls and counts calls in a time window`() {
		val first = repository.save(call(Instant.parse("2026-06-04T01:00:00Z")))
		repository.save(call(Instant.parse("2026-06-04T03:00:00Z")))
		repository.save(call(Instant.parse("2026-06-05T01:00:00Z")))

		assertThat(first.id).isNotNull()
		assertThat(
			repository.countByProviderNameAndCalledAtBetween(
				providerName = "Stormglass",
				startsAt = Instant.parse("2026-06-04T00:00:00Z"),
				endsAt = Instant.parse("2026-06-05T00:00:00Z"),
			),
		).isEqualTo(2)
	}

	private fun call(calledAt: Instant): ProviderCallLog =
		ProviderCallLog(
			id = null,
			providerName = "Stormglass",
			spotId = SpotId("ermitage"),
			calledForDate = LocalDate.parse("2026-06-04"),
			calledAt = calledAt,
			purpose = ProviderCallPurpose.TIDE_CACHE_PREFETCH,
			result = ProviderCallResult.SUCCESS,
			message = null,
		)
}
