package com.jawtrack.app.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import com.jawtrack.corelogic.audio.AudioRingBuffer

/**
 * Owns the [AudioRecord] lifecycle: source negotiation (UNPROCESSED -> MIC), disabling
 * platform noise suppression/AGC, and a dedicated urgent-priority reader thread that
 * copies samples into the ring buffer with no per-iteration allocation (JawTrackSpec §4.1,
 * §12). Never uses VOICE_RECOGNITION or VOICE_COMMUNICATION — their noise suppression
 * attacks the exact broadband signal grinding produces.
 */
class AudioCapture(
    private val context: Context,
    private val ringBuffer: AudioRingBuffer,
    private val listener: Listener
) {
    interface Listener {
        fun onCaptureStarted(audioSourceUsed: String)
        fun onGapStarted(atMillis: Long, reason: String)
        fun onGapEnded(atMillis: Long)
        fun onFatalError(t: Throwable)
    }

    @Volatile private var running = false
    private var readerThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    val isRunning: Boolean get() = running

    fun start() {
        check(readerThread == null) { "AudioCapture already started" }
        running = true
        readerThread = Thread(::runReaderLoop, "AudioCapture-Reader").apply { start() }
    }

    fun stop() {
        running = false
        readerThread?.join(2_000)
        readerThread = null
        releaseAudioRecord()
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is verified by the caller before starting the service
    private fun runReaderLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val (record, sourceUsed) = try {
            createAudioRecord()
        } catch (t: Throwable) {
            listener.onFatalError(t)
            running = false
            return
        }
        audioRecord = record
        record.startRecording()
        listener.onCaptureStarted(sourceUsed)

        val chunk = ShortArray(AudioConfig.READ_CHUNK_SAMPLES) // preallocated once, hot loop never allocates
        var inGap = false
        var backoffMillis = INITIAL_BACKOFF_MILLIS

        while (running) {
            val samplesRead = record.read(chunk, 0, chunk.size)

            if (samplesRead > 0 && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                ringBuffer.write(chunk, 0, samplesRead)
                if (inGap) {
                    listener.onGapEnded(System.currentTimeMillis())
                    inGap = false
                }
                backoffMillis = INITIAL_BACKOFF_MILLIS
            } else {
                if (!inGap) {
                    listener.onGapStarted(System.currentTimeMillis(), reasonFor(samplesRead, record))
                    inGap = true
                }
                if (!attemptRecovery(record)) {
                    Thread.sleep(backoffMillis)
                    backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
                }
            }
        }

        if (inGap) listener.onGapEnded(System.currentTimeMillis())
        releaseAudioRecord()
    }

    /** Re-initializes the AudioRecord in place after loss (mic preempted by a call, Assistant, etc.). */
    private fun attemptRecovery(current: AudioRecord): Boolean {
        return try {
            current.stop()
            current.startRecording()
            current.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (_: Exception) {
            false
        }
    }

    private fun reasonFor(samplesRead: Int, record: AudioRecord): String = when {
        samplesRead == AudioRecord.ERROR_INVALID_OPERATION -> "invalid_operation"
        samplesRead == AudioRecord.ERROR_BAD_VALUE -> "bad_value"
        samplesRead == AudioRecord.ERROR_DEAD_OBJECT -> "dead_object"
        samplesRead == AudioRecord.ERROR -> "unknown_error"
        record.recordingState != AudioRecord.RECORDSTATE_RECORDING -> "recording_stopped"
        else -> "no_samples"
    }

    private fun createAudioRecord(): Pair<AudioRecord, String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val supportsUnprocessed =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        val sourcesToTry = buildList {
            if (supportsUnprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to AudioConfig.SOURCE_UNPROCESSED)
            add(MediaRecorder.AudioSource.MIC to AudioConfig.SOURCE_MIC)
        }

        var lastError: Exception? = null
        for ((source, name) in sourcesToTry) {
            try {
                val minBufferBytes = AudioRecord.getMinBufferSize(
                    AudioConfig.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferBytes <= 0) continue

                val record = AudioRecord(
                    source,
                    AudioConfig.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferBytes * 4
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    disableUnwantedEffects(record)
                    return record to name
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Failed to initialize AudioRecord with source $name", e)
            }
        }
        throw lastError ?: IllegalStateException("Unable to initialize AudioRecord on any source")
    }

    /** Explicitly disables NS/AGC if the platform attached them anyway (§4.1). */
    private fun disableUnwantedEffects(record: AudioRecord) {
        val sessionId = record.audioSessionId
        try {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.enabled = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable NoiseSuppressor", e)
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.enabled = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable AutomaticGainControl", e)
        }
    }

    private fun releaseAudioRecord() {
        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            } catch (_: Exception) {
                // already stopped/dead
            }
            it.release()
        }
        audioRecord = null
    }

    companion object {
        private const val TAG = "AudioCapture"
        private const val INITIAL_BACKOFF_MILLIS = 500L
        private const val MAX_BACKOFF_MILLIS = 30_000L
    }
}
