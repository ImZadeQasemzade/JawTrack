package com.jawtrack.corelogic.calibration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoomProfileJsonTest {

    @Test
    fun `round-trips a band floor map through encode and decode`() {
        val original = mapOf(25.0 to -55.3, 1000.0 to -42.1, 8000.0 to -60.0)

        val decoded = RoomProfileJson.decode(RoomProfileJson.encode(original))

        assertEquals(original.size, decoded.size)
        original.forEach { (band, db) -> assertEquals(db, decoded.getValue(band), 0.0001) }
    }

    @Test
    fun `encode produces valid-looking flat JSON object syntax`() {
        val json = RoomProfileJson.encode(mapOf(100.0 to -50.0))

        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"100.0\":-50.0"))
    }

    @Test
    fun `decode of an empty object yields an empty map`() {
        assertTrue(RoomProfileJson.decode("{}").isEmpty())
    }

    @Test
    fun `handles negative values on both keys and values correctly`() {
        val original = mapOf(-1.0 to -99.9) // key won't occur in practice but exercises the sign parsing
        val decoded = RoomProfileJson.decode(RoomProfileJson.encode(original))
        assertEquals(-99.9, decoded.getValue(-1.0), 0.0001)
    }
}
