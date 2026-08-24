package com.jawtrack.app.ml

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import com.jawtrack.corelogic.detection.Gate2Classification
import com.jawtrack.app.recording.AudioConfig

/**
 * YAMNet via MediaPipe's `AudioClassifier` task (JawTrackSpec §4.7, D5).
 *
 * **Unverified.** This was written without Android SDK or network access to the actual
 * `com.google.mediapipe:tasks-audio` artifact or a YAMNet `.tflite` model file — both are
 * required and neither is available in the sandbox this was authored in. Before relying on
 * this class:
 * 1. Add the dependency (see `app/build.gradle.kts`) and confirm the API surface below
 *    against the real `tasks-audio` docs — builder method names in particular are a best
 *    reconstruction, not a compiled-and-checked call.
 * 2. Add a real YAMNet `.tflite` to `app/src/main/assets/` under [DEFAULT_MODEL_ASSET_PATH]
 *    (or pass a different path) — none is bundled.
 * 3. The spec calls for STREAM mode; this uses AUDIO_CLIPS mode instead, because
 *    [com.jawtrack.app.detection.FrameWindower] already pulls discrete fixed-size windows out
 *    of the ring buffer by polling rather than a continuous push-stream callback (the same
 *    "don't burden the audio thread" reasoning as `CalibrationSampler` in Phase 2) — each poll
 *    already *is* one complete clip, so a synchronous classify() call is the honest fit for
 *    this architecture, not a deviation that changes behavior.
 *
 * If construction throws (missing asset, wrong API surface, anything) the caller is expected
 * to catch it and run without Gate 2 rather than let a bad model take down the whole night's
 * recording — see how this is constructed in `RecordingService`.
 */
class YamnetGate2Classifier(
    context: Context,
    modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH
) : Gate2Classifier {

    private val classifier: AudioClassifier = AudioClassifier.createFromOptions(
        context,
        AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setMaxResults(MAX_RESULTS)
            .build()
    )

    override fun classify(pcmMono16kHz: FloatArray): Gate2Result {
        val format = AudioData.AudioDataFormat.create(CHANNEL_COUNT, AudioConfig.SAMPLE_RATE_HZ.toFloat())
        val audioData = AudioData.create(format, pcmMono16kHz.size)
        audioData.load(pcmMono16kHz)

        val result: AudioClassifierResult = classifier.classify(audioData)
        val categories = result.classificationResult()
            .classifications()
            .firstOrNull()
            ?.categories()
            .orEmpty()

        return Gate2Result(
            classifications = categories.map { Gate2Classification(it.categoryName(), it.score()) },
            embedding = null // MediaPipe's classifier task exposes categories only (§4.7)
        )
    }

    override fun close() {
        classifier.close()
    }

    companion object {
        const val DEFAULT_MODEL_ASSET_PATH = "yamnet.tflite"
        private const val MAX_RESULTS = 8
        private const val CHANNEL_COUNT = 1
    }
}
