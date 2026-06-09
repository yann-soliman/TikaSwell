package ovh.tika.tikaswell.infrastructure.persistence

import java.sql.ResultSet

internal fun ResultSet.nullableDouble(column: String): Double? {
	val value = getDouble(column)
	return if (wasNull()) null else value
}

internal fun ResultSet.nullableInt(column: String): Int? {
	val value = getInt(column)
	return if (wasNull()) null else value
}
