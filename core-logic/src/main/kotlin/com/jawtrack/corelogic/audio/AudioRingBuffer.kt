package com.jawtrack.corelogic.audio

/**
 * Fixed-capacity circular buffer of 16-bit PCM samples, sized for a rolling window
 * (JawTrackSpec §4.4: 30 s at 16 kHz mono ≈ 480,000 samples ≈ 960 KB).
 *
 * [write] is the hot-loop entry point from the audio reader thread: it never allocates.
 * [snapshot] allocates a new array and is only meant to be called rarely, when an episode
 * is detected and the pre-onset audio needs to be pulled out for a clip.
 *
 * Not thread-safe by locking — callers must ensure a single writer thread (the audio
 * reader) and treat [snapshot] reads as happening from another thread only after the
 * writer has produced enough data; a lightweight synchronized wrapper is intentionally
 * left to the caller so the hot path stays lock-free where possible.
 */
class AudioRingBuffer(val capacitySamples: Int) {

    init {
        require(capacitySamples > 0) { "capacitySamples must be positive" }
    }

    private val buffer = ShortArray(capacitySamples)

    /** Index one past the most recently written sample, mod capacity. */
    private var writeIndex = 0

    /** Total samples ever written, unbounded — used to know how much of the buffer is valid. */
    private var totalWritten = 0L

    val samplesAvailable: Int
        get() = minOf(totalWritten, capacitySamples.toLong()).toInt()

    /** Appends [length] samples from [source] starting at [offset]. Never allocates. */
    @Synchronized
    fun write(source: ShortArray, offset: Int = 0, length: Int = source.size) {
        require(offset >= 0 && length >= 0 && offset + length <= source.size) {
            "invalid offset/length for source of size ${source.size}"
        }
        var remaining = length
        var srcPos = offset
        while (remaining > 0) {
            val chunk = minOf(remaining, capacitySamples - writeIndex)
            System.arraycopy(source, srcPos, buffer, writeIndex, chunk)
            writeIndex = (writeIndex + chunk) % capacitySamples
            srcPos += chunk
            remaining -= chunk
        }
        totalWritten += length
    }

    /**
     * Returns a newly-allocated, chronologically-ordered copy of everything currently
     * buffered (oldest sample first). Length equals [samplesAvailable].
     */
    @Synchronized
    fun snapshot(): ShortArray {
        val available = samplesAvailable
        val result = ShortArray(available)
        if (available == 0) return result
        val startIndex = if (totalWritten < capacitySamples) {
            0
        } else {
            writeIndex // oldest sample is exactly where the next write will land
        }
        val firstChunk = minOf(available, capacitySamples - startIndex)
        System.arraycopy(buffer, startIndex, result, 0, firstChunk)
        val remaining = available - firstChunk
        if (remaining > 0) {
            System.arraycopy(buffer, 0, result, firstChunk, remaining)
        }
        return result
    }

    /** Returns the most recent [n] samples (or fewer if not yet available), oldest first. */
    fun snapshotLast(n: Int): ShortArray {
        val full = snapshot()
        if (n >= full.size) return full
        return full.copyOfRange(full.size - n, full.size)
    }

    @Synchronized
    fun clear() {
        writeIndex = 0
        totalWritten = 0
    }
}
