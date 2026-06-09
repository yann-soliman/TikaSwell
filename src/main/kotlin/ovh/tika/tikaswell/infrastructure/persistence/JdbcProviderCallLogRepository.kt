package ovh.tika.tikaswell.infrastructure.persistence

import ovh.tika.tikaswell.application.tide.ProviderCallLogRepository
import ovh.tika.tikaswell.domain.ProviderCallLog
import ovh.tika.tikaswell.domain.ProviderCallLogId
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JdbcProviderCallLogRepository(
	private val jdbcTemplate: NamedParameterJdbcTemplate,
) : ProviderCallLogRepository {
	override fun save(call: ProviderCallLog): ProviderCallLog {
		val keyHolder = GeneratedKeyHolder()
		jdbcTemplate.update(
			"""
			INSERT INTO provider_call_log (
			  provider_name,
			  spot_id,
			  called_for_date,
			  called_at,
			  purpose,
			  result,
			  message
			)
			VALUES (
			  :providerName,
			  :spotId,
			  :calledForDate,
			  :calledAt,
			  :purpose,
			  :result,
			  :message
			)
			""".trimIndent(),
			MapSqlParameterSource()
				.addValue("providerName", call.providerName)
				.addValue("spotId", call.spotId?.value)
				.addValue("calledForDate", call.calledForDate?.toString())
				.addValue("calledAt", call.calledAt.toString())
				.addValue("purpose", call.purpose.name)
				.addValue("result", call.result.name)
				.addValue("message", call.message),
			keyHolder,
		)

		return call.copy(id = ProviderCallLogId(keyHolder.key!!.toLong()))
	}

	override fun countByProviderNameAndCalledAtBetween(providerName: String, startsAt: Instant, endsAt: Instant): Int =
		jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM provider_call_log
			WHERE provider_name = :providerName
			  AND called_at >= :startsAt
			  AND called_at < :endsAt
			""".trimIndent(),
			mapOf(
				"providerName" to providerName,
				"startsAt" to startsAt.toString(),
				"endsAt" to endsAt.toString(),
			),
			Int::class.java,
		) ?: 0
}
