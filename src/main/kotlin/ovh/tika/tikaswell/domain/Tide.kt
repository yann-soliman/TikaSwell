package ovh.tika.tikaswell.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@JvmInline
value class TideDayCacheId(val value: Long) {
	init {
		require(value > 0) { "L'id du cache de marée doit être positif" }
	}
}

@JvmInline
value class ProviderCallLogId(val value: Long) {
	init {
		require(value > 0) { "L'id du journal d'appel provider doit être positif" }
	}
}

enum class TidePhase {
	RISING,
	FALLING,
	HIGH,
	LOW,
	UNKNOWN,
}

enum class TideEventType {
	HIGH,
	LOW,
}

enum class TideUnavailableReason {
	CACHE_MISS,
	MISSING_API_KEY,
	QUOTA_REACHED,
	PROVIDER_UNAVAILABLE,
}

enum class ProviderCallPurpose {
	TIDE_CACHE_PREFETCH,
	TIDE_CACHE_MISS,
	MANUAL_REFRESH,
}

enum class ProviderCallResult {
	SUCCESS,
	FAILURE,
	SKIPPED,
}

data class TideEvent(
	val type: TideEventType,
	val timestamp: Instant,
	val waterHeightMeters: Double?,
)

data class TidePoint(
	val timestamp: Instant,
	val waterHeightMeters: Double?,
)

data class TideSnapshot(
	val spotId: SpotId,
	val timestamp: Instant,
	val waterHeightMeters: Double?,
	val phase: TidePhase?,
	val previousHighTide: TideEvent?,
	val previousLowTide: TideEvent?,
	val nextHighTide: TideEvent?,
	val nextLowTide: TideEvent?,
	val timeSincePreviousHighTide: Duration?,
	val timeSincePreviousLowTide: Duration?,
	val timeUntilNextHighTide: Duration?,
	val timeUntilNextLowTide: Duration?,
	val coefficient: Double?,
	val providerName: String,
) {
	init {
		timeSincePreviousHighTide?.let { require(!it.isNegative) { "Le délai depuis la pleine mer doit être positif ou nul" } }
		timeSincePreviousLowTide?.let { require(!it.isNegative) { "Le délai depuis la basse mer doit être positif ou nul" } }
		timeUntilNextHighTide?.let { require(!it.isNegative) { "Le délai avant la pleine mer doit être positif ou nul" } }
		timeUntilNextLowTide?.let { require(!it.isNegative) { "Le délai avant la basse mer doit être positif ou nul" } }
		coefficient?.let { require(it in 0.0..120.0) { "Le coefficient de marée doit être compris entre 0 et 120" } }
		require(providerName.isNotBlank()) { "Le nom du fournisseur de marée est obligatoire" }
	}
}

data class TideDayCache(
	val id: TideDayCacheId?,
	val spotId: SpotId,
	val date: LocalDate,
	val providerName: String,
	val fetchedAt: Instant,
	val stationName: String?,
	val stationDistanceKilometers: Double?,
	val coefficient: Double?,
	val unavailableReason: TideUnavailableReason?,
	val unavailableMessage: String?,
	val points: List<TidePoint>,
	val events: List<TideEvent>,
) {
	init {
		require(providerName.isNotBlank()) { "Le nom du fournisseur de marée est obligatoire" }
		stationName?.let { require(it.isNotBlank()) { "Le nom de station ne doit pas être vide" } }
		stationDistanceKilometers?.let { require(it >= 0.0) { "La distance de station doit être positive ou nulle" } }
		coefficient?.let { require(it in 0.0..120.0) { "Le coefficient de marée doit être compris entre 0 et 120" } }
	}
}

data class ProviderCallLog(
	val id: ProviderCallLogId?,
	val providerName: String,
	val spotId: SpotId?,
	val calledForDate: LocalDate?,
	val calledAt: Instant,
	val purpose: ProviderCallPurpose,
	val result: ProviderCallResult,
	val message: String?,
) {
	init {
		require(providerName.isNotBlank()) { "Le nom du fournisseur appelé est obligatoire" }
	}
}
