package com.jawtrack.corelogic.enrichment

/** Tiny hand-rolled codec for a flat `{stageName: episodeCount}` object — backs `NightMetrics.stageDistributionJson` (§7). */
object StageDistributionJson {

    fun encode(distribution: Map<String, Int>): String =
        distribution.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (stage, count) -> "\"$stage\":$count" }

    private val ENTRY_PATTERN = Regex("\"([A-Za-z_]+)\":(\\d+)")

    fun decode(json: String): Map<String, Int> =
        ENTRY_PATTERN.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toInt() }
}
