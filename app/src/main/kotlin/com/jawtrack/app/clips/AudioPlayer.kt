package com.jawtrack.app.clips

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.jawtrack.app.recording.AudioConfig

/**
 * Plays a decrypted clip straight from memory (§8 Screen 3: "decrypt to memory, never to a
 * temp file"). One-shot: a fresh `AudioTrack` per [play] call sized exactly to the clip —
 * simpler and safer than a reusable streaming track for something this short (<=12s, §6.3).
 */
class AudioPlayer {

    private var currentTrack: AudioTrack? = null

    fun play(pcm16Bit: ShortArray, sampleRateHz: Int = AudioConfig.SAMPLE_RATE_HZ) {
        stop()
        if (pcm16Bit.isEmpty()) return

        val minBufferBytes = AudioTrack.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(minBufferBytes, pcm16Bit.size * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm16Bit, 0, pcm16Bit.size)
        track.play()
        currentTrack = track
    }

    fun stop() {
        currentTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            } catch (_: IllegalStateException) {
                // already stopped/released
            }
            track.release()
        }
        currentTrack = null
    }
}
