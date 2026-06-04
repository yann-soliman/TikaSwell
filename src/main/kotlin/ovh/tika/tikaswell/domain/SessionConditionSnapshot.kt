package ovh.tika.tikaswell.domain

@JvmInline
value class ConditionSnapshotId(val value: Long) {
	init {
		require(value > 0) { "L'id du snapshot de conditions doit être positif" }
	}
}

data class SessionConditionSnapshot(
	val id: ConditionSnapshotId?,
	val sessionId: SurfSessionId,
	val snapshot: ConditionSnapshot,
)
