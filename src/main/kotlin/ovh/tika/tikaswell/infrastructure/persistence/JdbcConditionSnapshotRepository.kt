package ovh.tika.tikaswell.infrastructure.persistence

import ovh.tika.tikaswell.application.conditions.ConditionSnapshotRepository
import ovh.tika.tikaswell.domain.ConditionSnapshot
import ovh.tika.tikaswell.domain.ConditionSnapshotId
import ovh.tika.tikaswell.domain.Direction
import ovh.tika.tikaswell.domain.SessionConditionSnapshot
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.SurfSessionId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcConditionSnapshotRepository(
	private val jdbcTemplate: NamedParameterJdbcTemplate,
) : ConditionSnapshotRepository {
	override fun saveForSession(sessionId: SurfSessionId, snapshot: ConditionSnapshot): SessionConditionSnapshot {
		val keyHolder = GeneratedKeyHolder()
		jdbcTemplate.update(
			"""
			INSERT INTO condition_snapshots (
			  surf_session_id,
			  spot_id,
			  observed_at,
			  wind_speed_kmh,
			  wind_gust_kmh,
			  wind_direction_degrees,
			  wave_height_meters,
			  wave_period_seconds,
			  wave_peak_period_seconds,
			  wave_direction_degrees,
			  wind_wave_height_meters,
			  wind_wave_period_seconds,
			  wind_wave_peak_period_seconds,
			  wind_wave_direction_degrees,
			  swell_wave_height_meters,
			  swell_wave_period_seconds,
			  swell_wave_peak_period_seconds,
			  swell_wave_direction_degrees,
			  provider_name
			)
			VALUES (
			  :sessionId,
			  :spotId,
			  :observedAt,
			  :windSpeedKmh,
			  :windGustKmh,
			  :windDirectionDegrees,
			  :waveHeightMeters,
			  :wavePeriodSeconds,
			  :wavePeakPeriodSeconds,
			  :waveDirectionDegrees,
			  :windWaveHeightMeters,
			  :windWavePeriodSeconds,
			  :windWavePeakPeriodSeconds,
			  :windWaveDirectionDegrees,
			  :swellWaveHeightMeters,
			  :swellWavePeriodSeconds,
			  :swellWavePeakPeriodSeconds,
			  :swellWaveDirectionDegrees,
			  :providerName
			)
			""".trimIndent(),
			MapSqlParameterSource()
				.addValue("sessionId", sessionId.value)
				.addValue("spotId", snapshot.spotId.value)
				.addValue("observedAt", snapshot.timestamp.toString())
				.addValue("windSpeedKmh", snapshot.windSpeedKmh)
				.addValue("windGustKmh", snapshot.windGustKmh)
				.addValue("windDirectionDegrees", snapshot.windDirection?.degrees)
				.addValue("waveHeightMeters", snapshot.waveHeightMeters)
				.addValue("wavePeriodSeconds", snapshot.wavePeriodSeconds)
				.addValue("wavePeakPeriodSeconds", snapshot.wavePeakPeriodSeconds)
				.addValue("waveDirectionDegrees", snapshot.waveDirection?.degrees)
				.addValue("windWaveHeightMeters", snapshot.windWaveHeightMeters)
				.addValue("windWavePeriodSeconds", snapshot.windWavePeriodSeconds)
				.addValue("windWavePeakPeriodSeconds", snapshot.windWavePeakPeriodSeconds)
				.addValue("windWaveDirectionDegrees", snapshot.windWaveDirection?.degrees)
				.addValue("swellWaveHeightMeters", snapshot.swellWaveHeightMeters)
				.addValue("swellWavePeriodSeconds", snapshot.swellWavePeriodSeconds)
				.addValue("swellWavePeakPeriodSeconds", snapshot.swellWavePeakPeriodSeconds)
				.addValue("swellWaveDirectionDegrees", snapshot.swellWaveDirection?.degrees)
				.addValue("providerName", snapshot.providerName),
			keyHolder,
		)

		return SessionConditionSnapshot(
			id = ConditionSnapshotId(keyHolder.key!!.toLong()),
			sessionId = sessionId,
			snapshot = snapshot,
		)
	}

	override fun findBySessionId(sessionId: SurfSessionId): List<SessionConditionSnapshot> =
		querySnapshots(
			"""
			SELECT id, surf_session_id, spot_id, observed_at, wind_speed_kmh, wind_gust_kmh,
			       wind_direction_degrees, wave_height_meters, wave_period_seconds,
			       wave_peak_period_seconds, wave_direction_degrees,
			       wind_wave_height_meters, wind_wave_period_seconds, wind_wave_peak_period_seconds,
			       wind_wave_direction_degrees, swell_wave_height_meters, swell_wave_period_seconds,
			       swell_wave_peak_period_seconds, swell_wave_direction_degrees, provider_name
			FROM condition_snapshots
			WHERE surf_session_id = :sessionId
			ORDER BY observed_at ASC
			""".trimIndent(),
			mapOf("sessionId" to sessionId.value),
		)

	override fun findBySpotId(spotId: SpotId): List<SessionConditionSnapshot> =
		querySnapshots(
			"""
			SELECT id, surf_session_id, spot_id, observed_at, wind_speed_kmh, wind_gust_kmh,
			       wind_direction_degrees, wave_height_meters, wave_period_seconds,
			       wave_peak_period_seconds, wave_direction_degrees,
			       wind_wave_height_meters, wind_wave_period_seconds, wind_wave_peak_period_seconds,
			       wind_wave_direction_degrees, swell_wave_height_meters, swell_wave_period_seconds,
			       swell_wave_peak_period_seconds, swell_wave_direction_degrees, provider_name
			FROM condition_snapshots
			WHERE spot_id = :spotId
			ORDER BY observed_at DESC
			""".trimIndent(),
			mapOf("spotId" to spotId.value),
		)

	private fun querySnapshots(sql: String, params: Map<String, Any>): List<SessionConditionSnapshot> =
		jdbcTemplate.query(sql, params, rowMapper)

	private companion object {
		val rowMapper = RowMapper { rs: ResultSet, _: Int ->
			// Le mapping reste volontairement explicite pour isoler les noms SQL du modèle canonique.
			SessionConditionSnapshot(
				id = ConditionSnapshotId(rs.getLong("id")),
				sessionId = SurfSessionId(rs.getLong("surf_session_id")),
				snapshot = ConditionSnapshot(
					spotId = SpotId(rs.getString("spot_id")),
					timestamp = Instant.parse(rs.getString("observed_at")),
					windSpeedKmh = rs.getDouble("wind_speed_kmh"),
					windGustKmh = rs.nullableDouble("wind_gust_kmh"),
					windDirection = rs.nullableInt("wind_direction_degrees")?.let(::Direction),
					waveHeightMeters = rs.nullableDouble("wave_height_meters"),
					wavePeriodSeconds = rs.nullableDouble("wave_period_seconds"),
					wavePeakPeriodSeconds = rs.nullableDouble("wave_peak_period_seconds"),
					waveDirection = rs.nullableInt("wave_direction_degrees")?.let(::Direction),
					windWaveHeightMeters = rs.nullableDouble("wind_wave_height_meters"),
					windWavePeriodSeconds = rs.nullableDouble("wind_wave_period_seconds"),
					windWavePeakPeriodSeconds = rs.nullableDouble("wind_wave_peak_period_seconds"),
					windWaveDirection = rs.nullableInt("wind_wave_direction_degrees")?.let(::Direction),
					swellWaveHeightMeters = rs.nullableDouble("swell_wave_height_meters"),
					swellWavePeriodSeconds = rs.nullableDouble("swell_wave_period_seconds"),
					swellWavePeakPeriodSeconds = rs.nullableDouble("swell_wave_peak_period_seconds"),
					swellWaveDirection = rs.nullableInt("swell_wave_direction_degrees")?.let(::Direction),
					providerName = rs.getString("provider_name"),
				),
			)
		}
	}
}
