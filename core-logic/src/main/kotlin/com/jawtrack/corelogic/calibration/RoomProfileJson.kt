package com.jawtrack.corelogic.calibration

/**
 * Tiny hand-rolled codec for a flat `{centerHz: floorDb}` object — avoids pulling in a JSON
 * library for what's a dozen number pairs. Backs `RoomProfile.bandNoiseFloorJson` (§7).
 */
object RoomProfileJson {

    fun encode(bandFloorsDb: Map<Double, Double>): String =
        bandFloorsDb.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (band, db) -> "\"$band\":$db" }

    private val ENTRY_PATTERN = Regex("\"(-?[0-9.]+)\":(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)")

    fun decode(json: String): Map<Double, Double> =
        ENTRY_PATTERN.findAll(json).associate { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
}
