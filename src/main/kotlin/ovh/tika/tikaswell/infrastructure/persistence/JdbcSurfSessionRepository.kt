package ovh.tika.tikaswell.infrastructure.persistence

import ovh.tika.tikaswell.application.session.SurfSessionRepository
import ovh.tika.tikaswell.domain.Rating
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSession
import ovh.tika.tikaswell.domain.SurfSessionId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcSurfSessionRepository(
	private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SurfSessionRepository {
	override fun save(session: SurfSession): SurfSession {
		require(session.id == null) { "Updating surf sessions is not implemented yet" }

		val keyHolder = GeneratedKeyHolder()
		jdbcTemplate.update(
			"""
			INSERT INTO surf_sessions (spot_id, starts_at, ends_at, rating, notes)
			VALUES (:spotId, :startsAt, :endsAt, :rating, :notes)
			""".trimIndent(),
			MapSqlParameterSource()
				.addValue("spotId", session.spotId.value)
				.addValue("startsAt", session.startsAt.toString())
				.addValue("endsAt", session.endsAt.toString())
				.addValue("rating", session.rating.value)
				.addValue("notes", session.notes),
			keyHolder,
		)

		return session.copy(id = SurfSessionId(keyHolder.key!!.toLong()))
	}

	override fun findById(id: SurfSessionId): SurfSession? =
		jdbcTemplate.query(
			"""
			SELECT id, spot_id, starts_at, ends_at, rating, notes
			FROM surf_sessions
			WHERE id = :id
			""".trimIndent(),
			mapOf("id" to id.value),
			rowMapper,
		).firstOrNull()

	override fun findBySpotId(spotId: SpotId): List<SurfSession> =
		jdbcTemplate.query(
			"""
			SELECT id, spot_id, starts_at, ends_at, rating, notes
			FROM surf_sessions
			WHERE spot_id = :spotId
			ORDER BY starts_at DESC
			""".trimIndent(),
			mapOf("spotId" to spotId.value),
			rowMapper,
		)

	private companion object {
		val rowMapper = RowMapper { rs: ResultSet, _: Int ->
			SurfSession(
				id = SurfSessionId(rs.getLong("id")),
				spotId = SpotId(rs.getString("spot_id")),
				startsAt = Instant.parse(rs.getString("starts_at")),
				endsAt = Instant.parse(rs.getString("ends_at")),
				rating = Rating(rs.getInt("rating")),
				notes = rs.getString("notes"),
			)
		}
	}
}
