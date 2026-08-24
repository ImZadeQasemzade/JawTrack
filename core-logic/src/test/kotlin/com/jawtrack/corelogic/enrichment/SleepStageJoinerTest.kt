package com.jawtrack.corelogic.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SleepStageJoinerTest {

    private val stages = listOf(
        SleepStageInterval(0, 60_000, "AWAKE"),
        SleepStageInterval(60_000, 300_000, "LIGHT"),
        SleepStageInterval(300_000, 420_000, "DEEP"),
        SleepStageInterval(420_000, 480_000, "REM")
    )

    @Test
    fun `an episode fully inside one stage joins to that stage`() {
        assertEquals("LIGHT", SleepStageJoiner.dominantStage(100_000, 150_000, stages))
    }

    @Test
    fun `an episode straddling two stages joins to whichever it overlaps more`() {
        // 290s-330s: 10s in LIGHT (290-300), 30s in DEEP (300-330) -- DEEP wins
        assertEquals("DEEP", SleepStageJoiner.dominantStage(290_000, 330_000, stages))
    }

    @Test
    fun `an episode with no covering stage data returns null`() {
        assertNull(SleepStageJoiner.dominantStage(1_000_000, 1_010_000, stages))
    }

    @Test
    fun `an episode partially before recorded stages only counts the covered portion`() {
        // -50s to 30s: only 0-30s (30s) overlaps AWAKE; the rest is before any stage data
        assertEquals("AWAKE", SleepStageJoiner.dominantStage(-50_000, 30_000, stages))
    }

    @Test
    fun `totalSleepDurationHours excludes AWAKE and OUT_OF_BED`() {
        val hours = SleepStageJoiner.totalSleepDurationHours(stages)

        // LIGHT(240s) + DEEP(120s) + REM(60s) = 420s = 0.116666h; AWAKE(60s) excluded
        assertEquals(420_000.0 / 3_600_000.0, hours, 0.0001)
    }

    @Test
    fun `totalSleepDurationHours of no stage data is zero`() {
        assertEquals(0.0, SleepStageJoiner.totalSleepDurationHours(emptyList()))
    }

    @Test
    fun `totalSleepDurationHours of all-awake data is zero`() {
        val allAwake = listOf(SleepStageInterval(0, 3_600_000, "AWAKE"))
        assertEquals(0.0, SleepStageJoiner.totalSleepDurationHours(allAwake))
    }
}
