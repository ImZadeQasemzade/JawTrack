package com.jawtrack.corelogic.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeAssemblerTest {

    private fun frame(t: Long, score: Double, db: Double = -20.0, band: Double? = 2000.0) =
        FrameResult(timestampMillis = t, score = score, db = db, dominantBandHz = band)

    @Test
    fun `a single-frame spike is discarded as too short`() {
        val assembler = EpisodeAssembler()
        assembler.process(frame(0, 0.9))

        val episodes = assembler.flush()

        assertTrue(episodes.isEmpty(), "a zero-duration single-frame run should never become an episode")
    }

    @Test
    fun `a sustained run above enter threshold becomes one episode on flush`() {
        val assembler = EpisodeAssembler()
        assembler.process(frame(0, 0.9))
        assembler.process(frame(500, 0.9))
        assembler.process(frame(1000, 0.9))

        val episodes = assembler.flush()

        assertEquals(1, episodes.size)
        val ep = episodes.single()
        assertEquals(0L, ep.onsetMillis)
        assertEquals(1000L, ep.offsetMillis)
        assertEquals(0.9, ep.peakScore, 0.0001)
        assertEquals(0.9, ep.meanScore, 0.0001)
    }

    @Test
    fun `hysteresis keeps the episode open while score dips between exit and enter`() {
        val assembler = EpisodeAssembler()
        assembler.process(frame(0, 0.8))    // enters
        assembler.process(frame(500, 0.5))  // dips, but still >= exit(0.4) -- stays open
        assembler.process(frame(1000, 0.9)) // rises again

        val episodes = assembler.flush()

        assertEquals(1, episodes.size, "the dip to 0.5 must not split the episode")
        assertEquals(0L, episodes.single().onsetMillis)
        assertEquals(1000L, episodes.single().offsetMillis)
    }

    @Test
    fun `score dropping below exit threshold ends the run`() {
        val assembler = EpisodeAssembler()
        val emittedDuringProcessing = mutableListOf<EpisodeCandidate>()

        emittedDuringProcessing += assembler.process(frame(0, 0.8))
        emittedDuringProcessing += assembler.process(frame(500, 0.9))
        emittedDuringProcessing += assembler.process(frame(1000, 0.2)) // drops below exit -> run ends, held pending
        emittedDuringProcessing += assembler.process(frame(10_000, 0.0)) // long silence -> pending times out and is emitted

        assertEquals(1, emittedDuringProcessing.size)
        assertEquals(500L, emittedDuringProcessing.single().offsetMillis)
    }

    /** process() can emit mid-stream (e.g. a pending episode timing out), not only on flush(). */
    private fun runAndCollect(assembler: EpisodeAssembler, frames: List<FrameResult>): List<EpisodeCandidate> {
        val collected = mutableListOf<EpisodeCandidate>()
        frames.forEach { collected += assembler.process(it) }
        collected += assembler.flush()
        return collected
    }

    @Test
    fun `two runs separated by less than the merge gap become one episode`() {
        val assembler = EpisodeAssembler(mergeGapMillis = 3_000)
        val episodes = runAndCollect(
            assembler,
            listOf(
                frame(0, 0.8),
                frame(500, 0.8),
                frame(1_000, 0.2),  // first run ends at t=500
                frame(2_500, 0.8),  // second run starts 2000ms after first run's offset -- within gap
                frame(3_000, 0.8),
                frame(3_500, 0.2)   // second run ends at t=3000
            )
        )

        assertEquals(1, episodes.size, "runs 2000ms apart should merge into a single episode")
        val ep = episodes.single()
        assertEquals(0L, ep.onsetMillis)
        assertEquals(3_000L, ep.offsetMillis)
    }

    @Test
    fun `two runs separated by more than the merge gap stay separate episodes`() {
        val assembler = EpisodeAssembler(mergeGapMillis = 3_000)
        val episodes = runAndCollect(
            assembler,
            listOf(
                frame(0, 0.8),
                frame(500, 0.8),
                frame(1_000, 0.2),  // first run ends at t=500
                frame(6_000, 0.8),  // 5500ms later -- outside the 3000ms merge gap
                frame(6_500, 0.8),
                frame(7_000, 0.2)   // second run ends at t=6500
            )
        )

        assertEquals(2, episodes.size)
        assertEquals(500L, episodes[0].offsetMillis)
        assertEquals(6_000L, episodes[1].onsetMillis)
    }

    @Test
    fun `an episode longer than the max duration is discarded`() {
        val assembler = EpisodeAssembler(maxDurationMillis = 30_000)
        assembler.process(frame(0, 0.8))
        assembler.process(frame(35_000, 0.8)) // 35s later, still one continuous absorbed run

        val episodes = assembler.flush()

        assertTrue(episodes.isEmpty(), "a >30s run should be discarded as noise, not RMMA")
    }

    @Test
    fun `peak and mean score, peak dB, and dominant band are aggregated correctly`() {
        val assembler = EpisodeAssembler()
        assembler.process(frame(0, 0.7001, db = -25.0, band = 1600.0))
        assembler.process(frame(400, 0.95, db = -10.0, band = 2000.0))
        assembler.process(frame(800, 0.75, db = -18.0, band = 2000.0))

        val ep = assembler.flush().single()

        assertEquals(0.95, ep.peakScore, 0.0001)
        assertEquals((0.7001 + 0.95 + 0.75) / 3.0, ep.meanScore, 0.0001)
        assertEquals(-10.0, ep.peakDb, 0.0001)
        assertEquals(2000.0, ep.dominantBandHz) // wins 2-1 over 1600Hz
    }

    @Test
    fun `rejected classes seen during the run are unioned onto the episode`() {
        val assembler = EpisodeAssembler()
        assembler.process(FrameResult(0, 0.8, -20.0, rejectedClasses = listOf("Vehicle")))
        assembler.process(FrameResult(400, 0.8, -20.0, rejectedClasses = listOf("Dog", "Vehicle")))

        val ep = assembler.flush().single()

        assertEquals(setOf("Vehicle", "Dog"), ep.rejectedClasses.toSet())
    }

    @Test
    fun `entering requires strictly greater than the enter threshold`() {
        val assembler = EpisodeAssembler(enterThreshold = 0.7)
        assembler.process(frame(0, 0.7)) // exactly at threshold -- must not enter
        assembler.process(frame(400, 0.7))

        val episodes = assembler.flush()

        assertTrue(episodes.isEmpty())
    }

    @Test
    fun `idle frames below enter threshold produce nothing`() {
        val assembler = EpisodeAssembler()
        val emitted = assembler.process(frame(0, 0.1))

        assertTrue(emitted.isEmpty())
        assertNull(assembler.flush().firstOrNull())
    }

    @Test
    fun `flush finalizes a still-open run at end of session`() {
        val assembler = EpisodeAssembler()
        assembler.process(frame(0, 0.8))
        assembler.process(frame(1_000, 0.8)) // never drops below exit before the night ends

        val episodes = assembler.flush()

        assertEquals(1, episodes.size)
        assertEquals(1_000L, episodes.single().offsetMillis)
    }
}
