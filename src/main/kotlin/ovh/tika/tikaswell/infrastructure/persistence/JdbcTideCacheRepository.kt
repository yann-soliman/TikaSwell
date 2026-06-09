package ovh.tika.tikaswell.infrastructure.persistence

import ovh.tika.tikaswell.application.tide.TideCacheRepository
import ovh.tika.tikaswell.domain.SpotId
import ovh.tika.tikaswell.domain.TideDayCache
import ovh.tika.tikaswell.domain.TideDayCacheId
import ovh.tika.tikaswell.domain.TideEvent
import ovh.tika.tikaswell.domain.TideEventType
import ovh.tika.tikaswell.domain.TidePoint
import ovh.tika.tikaswell.domain.TideUnavailableReason
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate

@Repository
class JdbcTideCacheRepository(
	private val jdbcTemplate: NamedParameterJdbcTemplate,
) : TideCacheRepository {
	@Transactional
	override fun save(cache: TideDayCache): TideDayCache {
		val existingId = findId(cache.spotId, cache.date, cache.providerName)
		if (existingId != null) {
			deleteById(existingId)
		}

		val cacheId = insertCache(cache)
		insertPoints(cacheId, cache.points)
		insertEvents(cacheId, cache.events)

		return cache.copy(id = TideDayCacheId(cacheId))
	}

	override fun findBySpotIdAndDateAndProvider(spotId: SpotId, date: LocalDate, providerName: String): TideDayCache? {
		val cache = jdbcTemplate.query(
			"""
			SELECT id, spot_id, tide_date, provider_name, fetched_at, station_name,
			       station_distance_kilometers, coefficient, unavailable_reason, unavailable_message
			FROM tide_day_cache
			WHERE spot_id = :spotId AND tide_date = :date AND provider_name = :providerName
			""".trimIndent(),
			mapOf(
				"spotId" to spotId.value,
				"date" to date.toString(),
				"providerName" to providerName,
			),
			cacheRowMapper,
		).firstOrNull() ?: return null

		return cache.copy(
			points = findPoints(cache.id!!),
			events = findEvents(cache.id),
		)
	}

	override fun existsBySpotIdAndDateAndProvider(spotId: SpotId, date: LocalDate, providerName: String): Boolean =
		findId(spotId, date, providerName) != null

	private fun insertCache(cache: TideDayCache): Long {
		val keyHolder = GeneratedKeyHolder()
		jdbcTemplate.update(
			"""
			INSERT INTO tide_day_cache (
			  spot_id,
			  tide_date,
			  provider_name,
			  fetched_at,
			  station_name,
			  station_distance_kilometers,
			  coefficient,
			  unavailable_reason,
			  unavailable_message
			)
			VALUES (
			  :spotId,
			  :date,
			  :providerName,
			  :fetchedAt,
			  :stationName,
			  :stationDistanceKilometers,
			  :coefficient,
			  :unavailableReason,
			  :unavailableMessage
			)
			""".trimIndent(),
			MapSqlParameterSource()
				.addValue("spotId", cache.spotId.value)
				.addValue("date", cache.date.toString())
				.addValue("providerName", cache.providerName)
				.addValue("fetchedAt", cache.fetchedAt.toString())
				.addValue("stationName", cache.stationName)
				.addValue("stationDistanceKilometers", cache.stationDistanceKilometers)
				.addValue("coefficient", cache.coefficient)
				.addValue("unavailableReason", cache.unavailableReason?.name)
				.addValue("unavailableMessage", cache.unavailableMessage),
			keyHolder,
		)
		return keyHolder.key!!.toLong()
	}

	private fun insertPoints(cacheId: Long, points: List<TidePoint>) {
		points.forEach { point ->
			jdbcTemplate.update(
				"""
				INSERT INTO tide_points (tide_day_cache_id, observed_at, water_height_meters)
				VALUES (:cacheId, :observedAt, :waterHeightMeters)
				""".trimIndent(),
				MapSqlParameterSource()
					.addValue("cacheId", cacheId)
					.addValue("observedAt", point.timestamp.toString())
					.addValue("waterHeightMeters", point.waterHeightMeters),
			)
		}
	}

	private fun insertEvents(cacheId: Long, events: List<TideEvent>) {
		events.forEach { event ->
			jdbcTemplate.update(
				"""
				INSERT INTO tide_events (tide_day_cache_id, event_type, occurs_at, water_height_meters)
				VALUES (:cacheId, :eventType, :occursAt, :waterHeightMeters)
				""".trimIndent(),
				MapSqlParameterSource()
					.addValue("cacheId", cacheId)
					.addValue("eventType", event.type.name)
					.addValue("occursAt", event.timestamp.toString())
					.addValue("waterHeightMeters", event.waterHeightMeters),
			)
		}
	}

	private fun findId(spotId: SpotId, date: LocalDate, providerName: String): Long? =
		jdbcTemplate.queryForList(
			"""
			SELECT id
			FROM tide_day_cache
			WHERE spot_id = :spotId AND tide_date = :date AND provider_name = :providerName
			""".trimIndent(),
			mapOf(
				"spotId" to spotId.value,
				"date" to date.toString(),
				"providerName" to providerName,
			),
			Long::class.java,
		).firstOrNull()

	private fun deleteById(id: Long) {
		val params = mapOf("id" to id)
		jdbcTemplate.update("DELETE FROM tide_points WHERE tide_day_cache_id = :id", params)
		jdbcTemplate.update("DELETE FROM tide_events WHERE tide_day_cache_id = :id", params)
		jdbcTemplate.update("DELETE FROM tide_day_cache WHERE id = :id", params)
	}

	private fun findPoints(cacheId: TideDayCacheId): List<TidePoint> =
		jdbcTemplate.query(
			"""
			SELECT observed_at, water_height_meters
			FROM tide_points
			WHERE tide_day_cache_id = :cacheId
			ORDER BY observed_at ASC
			""".trimIndent(),
			mapOf("cacheId" to cacheId.value),
		) { rs, _ ->
			TidePoint(
				timestamp = Instant.parse(rs.getString("observed_at")),
				waterHeightMeters = rs.nullableDouble("water_height_meters"),
			)
		}

	private fun findEvents(cacheId: TideDayCacheId): List<TideEvent> =
		jdbcTemplate.query(
			"""
			SELECT event_type, occurs_at, water_height_meters
			FROM tide_events
			WHERE tide_day_cache_id = :cacheId
			ORDER BY occurs_at ASC
			""".trimIndent(),
			mapOf("cacheId" to cacheId.value),
		) { rs, _ ->
			TideEvent(
				type = TideEventType.valueOf(rs.getString("event_type")),
				timestamp = Instant.parse(rs.getString("occurs_at")),
				waterHeightMeters = rs.nullableDouble("water_height_meters"),
			)
		}

	private companion object {
		val cacheRowMapper = RowMapper { rs: ResultSet, _: Int ->
			TideDayCache(
				id = TideDayCacheId(rs.getLong("id")),
				spotId = SpotId(rs.getString("spot_id")),
				date = LocalDate.parse(rs.getString("tide_date")),
				providerName = rs.getString("provider_name"),
				fetchedAt = Instant.parse(rs.getString("fetched_at")),
				stationName = rs.getString("station_name"),
				stationDistanceKilometers = rs.nullableDouble("station_distance_kilometers"),
				coefficient = rs.nullableDouble("coefficient"),
				unavailableReason = rs.getString("unavailable_reason")?.let(TideUnavailableReason::valueOf),
				unavailableMessage = rs.getString("unavailable_message"),
				points = emptyList(),
				events = emptyList(),
			)
		}
	}
}
