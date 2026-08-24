package com.jawtrack.corelogic.watchdog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeartbeatEvaluatorTest {

    @Test
    fun `clean shutdown is never flagged as silent death`() {
        val result = HeartbeatEvaluator.evaluate(
            cleanShutdown = true,
            lastHeartbeatAtMillis = 0L,
            referenceNowMillis = 10_000_000L
        )

        assertFalse(result.likelySilentDeath)
        assertNull(result.apparentStopAtMillis)
    }

    @Test
    fun `no heartbeat ever recorded is not silent death (never started recording)`() {
        val result = HeartbeatEvaluator.evaluate(
            cleanShutdown = false,
            lastHeartbeatAtMillis = null,
            referenceNowMillis = 10_000_000L
        )

        assertFalse(result.likelySilentDeath)
    }

    @Test
    fun `heartbeat trailing off two intervals ago with unclean shutdown is flagged as silent death`() {
        val lastBeat = 0L
        val now = 130_000L // 2 min 10s later, well past 2x the 60s interval

        val result = HeartbeatEvaluator.evaluate(
            cleanShutdown = false,
            lastHeartbeatAtMillis = lastBeat,
            referenceNowMillis = now
        )

        assertTrue(result.likelySilentDeath)
        assertEquals(lastBeat, result.apparentStopAtMillis)
        assertEquals(130_000L, result.gapSinceLastHeartbeatMillis)
    }

    @Test
    fun `unclean shutdown but heartbeat still fresh is not flagged (app just relaunched quickly)`() {
        val lastBeat = 100_000L
        val now = 105_000L // 5s since last beat, well under threshold

        val result = HeartbeatEvaluator.evaluate(
            cleanShutdown = false,
            lastHeartbeatAtMillis = lastBeat,
            referenceNowMillis = now
        )

        assertFalse(result.likelySilentDeath)
    }
}
