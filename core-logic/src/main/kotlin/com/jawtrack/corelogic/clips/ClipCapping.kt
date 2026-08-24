package com.jawtrack.corelogic.clips

/**
 * The clip-length hard cap (JawTrackSpec §6.3: "Clip length capped at 12 s (10 s pre-onset +
 * episode start), not the full episode if it runs long"). Pulled out as pure logic so it's
 * testable without the Android Keystore/File APIs `ClipStore` needs to actually write a clip —
 * §6/§12 both call for the privacy rules to have tests that fail loudly, and this is the one
 * piece of that guarantee that can run outside a device.
 */
object ClipCapping {
    const val MAX_CLIP_SECONDS = 12

    fun maxClipSamples(sampleRateHz: Int): Int = MAX_CLIP_SECONDS * sampleRateHz

    /** Truncates to the most recent [maxSamples] samples (keeping order) if longer; returns [samples] unchanged otherwise. */
    fun cap(samples: ShortArray, maxSamples: Int): ShortArray =
        if (samples.size > maxSamples) samples.copyOfRange(samples.size - maxSamples, samples.size) else samples
}
