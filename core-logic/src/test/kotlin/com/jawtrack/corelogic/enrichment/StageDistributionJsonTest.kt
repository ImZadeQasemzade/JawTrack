package com.jawtrack.corelogic.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StageDistributionJsonTest {

    @Test
    fun `round-trips a stage distribution through encode and decode`() {
        val original = mapOf("LIGHT" to 6, "DEEP" to 2, "REM" to 1)

        val decoded = StageDistributionJson.decode(StageDistributionJson.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `decode of an empty object yields an empty map`() {
        assertTrue(StageDistributionJson.decode("{}").isEmpty())
    }

    @Test
    fun `encode produces valid-looking flat JSON object syntax`() {
        val json = StageDistributionJson.encode(mapOf("LIGHT" to 3))
        assertEquals("{\"LIGHT\":3}", json)
    }
}
