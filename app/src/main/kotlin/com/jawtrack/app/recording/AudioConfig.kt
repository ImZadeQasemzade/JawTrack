package com.jawtrack.app.recording

/** Shared audio constants (JawTrackSpec §4.1, §4.4). 8 kHz Nyquist covers all grinding energy. */
object AudioConfig {
    const val SAMPLE_RATE_HZ = 16_000
    const val RING_BUFFER_SECONDS = 30
    const val RING_BUFFER_CAPACITY_SAMPLES = SAMPLE_RATE_HZ * RING_BUFFER_SECONDS

    /** 100 ms read chunks: small enough for responsive gap detection, large enough to avoid thrash. */
    const val READ_CHUNK_SAMPLES = SAMPLE_RATE_HZ / 10

    const val SOURCE_UNPROCESSED = "UNPROCESSED"
    const val SOURCE_MIC = "MIC"
}
