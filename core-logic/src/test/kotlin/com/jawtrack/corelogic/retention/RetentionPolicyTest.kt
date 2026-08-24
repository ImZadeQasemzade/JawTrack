package com.jawtrack.corelogic.retention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetentionPolicyTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `default 14 day retention computes expiry 14 days out`() {
        val expiresAt = RetentionPolicy.computeExpiresAt(createdAtMillis = 0, retentionDays = 14)
        assertEquals(14 * day, expiresAt)
    }

    @Test
    fun `retention below the minimum is clamped up to 3 days`() {
        val expiresAt = RetentionPolicy.computeExpiresAt(createdAtMillis = 0, retentionDays = 1)
        assertEquals(3 * day, expiresAt)
    }

    @Test
    fun `retention above the maximum is clamped down to 90 days`() {
        val expiresAt = RetentionPolicy.computeExpiresAt(createdAtMillis = 0, retentionDays = 365)
        assertEquals(90 * day, expiresAt)
    }

    @Test
    fun `expiry is relative to the clip's own creation time, not zero`() {
        val createdAt = 1_000_000L
        val expiresAt = RetentionPolicy.computeExpiresAt(createdAtMillis = createdAt, retentionDays = 3)
        assertEquals(createdAt + 3 * day, expiresAt)
    }

    @Test
    fun `isExpired is true exactly at expiry and after, false before`() {
        val expiresAt = 100_000L
        assertFalse(RetentionPolicy.isExpired(expiresAt, nowMillis = expiresAt - 1))
        assertTrue(RetentionPolicy.isExpired(expiresAt, nowMillis = expiresAt))
        assertTrue(RetentionPolicy.isExpired(expiresAt, nowMillis = expiresAt + 1))
    }
}
