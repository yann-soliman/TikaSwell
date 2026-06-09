package ovh.tika.tikaswell.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith
import java.time.Instant

class DomainModelTests {
	private val spotId = SpotId("initial")

	@Test
	fun `spot validates coordinates`() {
		assertFailsWith<IllegalArgumentException> {
			Spot(spotId, "Test spot", latitude = 91.0, longitude = 0.0)
		}

		assertFailsWith<IllegalArgumentException> {
			Spot(spotId, "Test spot", latitude = 0.0, longitude = 181.0)
		}
	}

	@Test
	fun `rating is bounded to a score out of ten`() {
		assertFailsWith<IllegalArgumentException> {
			Rating(11)
		}

		assertFailsWith<IllegalArgumentException> {
			Rating(-1)
		}
	}

	@Test
	fun `surf session requires a positive time window`() {
		val now = Instant.parse("2026-06-04T10:00:00Z")

		assertFailsWith<IllegalArgumentException> {
			SurfSession(
				id = null,
				spotId = spotId,
				startsAt = now,
				endsAt = now,
				rating = Rating(7),
				notes = null,
			)
		}
	}

	@Test
	fun `condition snapshot validates canonical weather and marine values`() {
		assertFailsWith<IllegalArgumentException> {
			ConditionSnapshot(
				spotId = spotId,
				timestamp = Instant.parse("2026-06-04T10:00:00Z"),
				windSpeedKmh = -1.0,
				windGustKmh = null,
				windDirection = Direction(270),
				waveHeightMeters = 1.2,
				wavePeriodSeconds = 8.0,
				wavePeakPeriodSeconds = -1.0,
				waveDirection = Direction(250),
				providerName = "Open-Meteo",
			)
		}

		assertFailsWith<IllegalArgumentException> {
			Direction(360)
		}
	}

	@Test
	fun `current score exposes bounded confidence and similarity`() {
		assertFailsWith<IllegalArgumentException> {
			CurrentScore(
				score = 7.0,
				confidence = 1.2,
				contributors = emptyList(),
				computedAt = Instant.parse("2026-06-04T10:00:00Z"),
			)
		}

		assertFailsWith<IllegalArgumentException> {
			ScoreContribution(
				sessionId = SurfSessionId(1),
				rating = Rating(8),
				similarity = -0.1,
			)
		}
	}
}
