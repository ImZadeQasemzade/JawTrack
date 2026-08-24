package com.jawtrack.corelogic.retention

/** Auto-retention for encrypted clips (JawTrackSpec §6.7): default 14 days, user range 3–90. */
object RetentionPolicy {
    const val DEFAULT_RETENTION_DAYS = 14
    const val MIN_RETENTION_DAYS = 3
    const val MAX_RETENTION_DAYS = 90

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    /** Clamps [retentionDays] into the allowed range before computing, so a bad setting can't silently keep clips forever (or delete them instantly). */
    fun computeExpiresAt(createdAtMillis: Long, retentionDays: Int): Long {
        val clampedDays = retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        return createdAtMillis + clampedDays * MILLIS_PER_DAY
    }

    fun isExpired(expiresAtMillis: Long, nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}
