package com.jawtrack.app.recording

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jawtrack.app.JawTrackApp
import com.jawtrack.app.data.db.entities.SessionState
import com.jawtrack.app.detection.FrameWindower
import com.jawtrack.app.ml.Gate2Classifier
import com.jawtrack.app.ml.YamnetGate2Classifier
import com.jawtrack.app.notification.RecordingNotificationManager
import com.jawtrack.corelogic.audio.AudioRingBuffer
import com.jawtrack.corelogic.calibration.RoomProfileJson
import com.jawtrack.corelogic.detection.EnergyGate
import com.jawtrack.corelogic.detection.EpisodeCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground recording service (JawTrackSpec §3, §4). Must be started from a visible
 * Activity — on API 34+ a microphone-type foreground service cannot be started from the
 * background, so this service assumes permissions are already granted and the phone is
 * already charging by the time it's asked to start; the UI layer is responsible for that.
 */
class RecordingService : Service(), AudioCapture.Listener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var notificationManager: RecordingNotificationManager
    private lateinit var ringBuffer: AudioRingBuffer
    private lateinit var audioCapture: AudioCapture
    private var calibrationSampler: CalibrationSampler? = null
    private var frameWindower: FrameWindower? = null
    private var gate2Classifier: Gate2Classifier? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var sessionId: Long = -1
    private var sessionStartedAt: Long = 0
    private var calibrationOnly: Boolean = false
    @Volatile private var episodeCount: Int = 0
    @Volatile private var currentGapStartedAt: Long? = null

    private val app: JawTrackApp get() = application as JawTrackApp

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> checkBatteryAfterUnplug()
                Intent.ACTION_BATTERY_CHANGED -> if (!isCharging()) checkBatteryAfterUnplug()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = RecordingNotificationManager(this)
        notificationManager.ensureChannel()
        ringBuffer = AudioRingBuffer(AudioConfig.RING_BUFFER_CAPACITY_SAMPLES)
        audioCapture = AudioCapture(this, ringBuffer, this)
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The system requires startForeground() within a few seconds of any
        // startForegroundService() call that created/woke this instance — including a
        // stop request racing a cold start — so this always runs first, before any
        // branch that might bail out early.
        ServiceCompat.startForeground(
            this,
            RecordingNotificationManager.NOTIFICATION_ID,
            notificationManager.build(elapsedMillis = 0, episodeCount = 0),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            else -> {
                calibrationOnly = intent?.getBooleanExtra(EXTRA_CALIBRATION_ONLY, false) ?: false
                beginSession()
            }
        }
        // Intentionally not START_STICKY: a system-restarted service with no active
        // AudioRecord/session state would be worse than no service — the heartbeat
        // watchdog is what makes a real death visible to the user on next launch (§4.6.2).
        return START_NOT_STICKY
    }

    private fun beginSession() {
        if (!isCharging()) {
            Log.w(TAG, "Refusing to start: device is not charging (§4.6.3)")
            releaseAndStop()
            return
        }

        acquireWakeLock()
        registerReceiver(batteryReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })

        sessionStartedAt = System.currentTimeMillis()

        serviceScope.launch {
            sessionId = app.sessionRepository.startSession(
                startedAt = sessionStartedAt,
                audioSourceUsed = AudioConfig.SOURCE_MIC, // updated once AudioCapture confirms the real source
                calibrationOnly = calibrationOnly
            )
            audioCapture.start()
            if (calibrationOnly) {
                calibrationSampler = CalibrationSampler(ringBuffer).also { it.start(serviceScope) }
            } else {
                startDetectionPipeline()
            }
            runHeartbeatLoop()
        }
        runNotificationTicker()
    }

    /** Wires Gates 1–3 + episode assembly (§4.7, §4.8) for a normal (non-calibration) night. */
    private suspend fun startDetectionPipeline() {
        val roomProfile = app.sessionRepository.getMostRecentRoomProfile()
        val energyGate = if (roomProfile != null) {
            EnergyGate.forRoomProfile(RoomProfileJson.decode(roomProfile.bandNoiseFloorJson))
        } else {
            // Shouldn't normally happen -- the UI forces a calibration night first (§4.5) -- but
            // a stale/missing profile must degrade to a conservative default, not crash the night.
            Log.w(TAG, "No RoomProfile available; using a conservative fallback Gate 1 threshold")
            EnergyGate(thresholdDb = FALLBACK_ENERGY_THRESHOLD_DB)
        }

        gate2Classifier = try {
            YamnetGate2Classifier(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Gate 2 (YAMNet) unavailable -- continuing with Gate 1 + heuristic Gate 3 only", e)
            null
        }

        frameWindower = FrameWindower(
            ringBuffer = ringBuffer,
            energyGate = energyGate,
            gate2Classifier = gate2Classifier,
            onEpisode = ::onEpisodeAssembled,
            onSpeechRejected = ::onSpeechRejected,
            onSnoringRejected = ::onSnoringRejected
        ).also { it.start(serviceScope) }
    }

    private fun onSpeechRejected() {
        val currentSessionId = sessionId
        if (currentSessionId >= 0) {
            serviceScope.launch { app.sessionRepository.incrementSpeechRejectedCount(currentSessionId) }
        }
    }

    private fun onSnoringRejected() {
        val currentSessionId = sessionId
        if (currentSessionId >= 0) {
            serviceScope.launch { app.sessionRepository.incrementSnoringRejectedCount(currentSessionId) }
        }
    }

    private fun onEpisodeAssembled(candidate: EpisodeCandidate) {
        episodeCount++
        val currentSessionId = sessionId
        if (currentSessionId >= 0) {
            serviceScope.launch {
                val episodeId = app.sessionRepository.saveEpisode(currentSessionId, candidate, CLASSIFIER_VERSION)
                val clipAudio = candidate.payload as? ShortArray
                if (clipAudio != null) {
                    app.clipRepository.saveClip(
                        episodeId = episodeId,
                        pcm16Bit = clipAudio,
                        sampleRateHz = AudioConfig.SAMPLE_RATE_HZ,
                        createdAtMillis = candidate.onsetMillis
                    )
                }
            }
        }
    }

    private fun runHeartbeatLoop() {
        serviceScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                if (sessionId >= 0) {
                    app.sessionRepository.recordHeartbeat(sessionId, System.currentTimeMillis())
                }
            }
        }
    }

    private fun runNotificationTicker() {
        serviceScope.launch {
            while (true) {
                delay(NOTIFICATION_TICK_MILLIS)
                val elapsed = System.currentTimeMillis() - sessionStartedAt
                notificationManager.notify(elapsed, episodeCount)
            }
        }
    }

    private fun checkBatteryAfterUnplug() {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0 until LOW_BATTERY_STOP_THRESHOLD_PCT) {
            Log.w(TAG, "Unplugged with battery at $level% — stopping cleanly (§4.6.3)")
            stopSelfCleanly()
        }
    }

    private fun isCharging(): Boolean {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.isCharging
    }

    private fun stopSelfCleanly() {
        serviceScope.launch {
            audioCapture.stop()
            frameWindower?.stop() // flushes any in-progress/pending episode (§4.8)
            frameWindower = null
            gate2Classifier?.close()
            gate2Classifier = null
            if (sessionId >= 0) {
                calibrationSampler?.let { sampler ->
                    sampler.stop()
                    sampler.finalizeProfile()?.let { floors ->
                        app.sessionRepository.saveRoomProfile(sessionId, floors, System.currentTimeMillis())
                    }
                }
                app.sessionRepository.endSession(
                    sessionId = sessionId,
                    startedAt = sessionStartedAt,
                    endedAt = System.currentTimeMillis(),
                    cleanShutdown = true
                )
            }
            releaseAndStop()
        }
    }

    private fun releaseAndStop() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
            // never registered (e.g. beginSession bailed out before registering)
        }
        releaseWakeLock()
        ServiceCompat.stopForeground(this, Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:overnight").apply {
            setReferenceCounted(false)
            acquire(MAX_SESSION_DURATION_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        // Best-effort: an OEM kill typically bypasses onDestroy entirely, which is exactly
        // why the heartbeat watchdog (not this method) is the source of truth for whether
        // a session ended cleanly.
        serviceJob.cancel()
        releaseWakeLock()
        gate2Classifier?.close()
        gate2Classifier = null
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
            // ignore
        }
        _isRunning.value = false
        super.onDestroy()
    }

    // --- AudioCapture.Listener ---

    override fun onCaptureStarted(audioSourceUsed: String) {
        Log.i(TAG, "Recording started with source=$audioSourceUsed")
    }

    override fun onGapStarted(atMillis: Long, reason: String) {
        currentGapStartedAt = atMillis
        Log.w(TAG, "Audio gap started: $reason")
    }

    override fun onGapEnded(atMillis: Long) {
        val start = currentGapStartedAt ?: return
        currentGapStartedAt = null
        if (sessionId >= 0) {
            serviceScope.launch {
                app.sessionRepository.recordGap(sessionId, start, atMillis, reason = "audio_interruption")
            }
        }
    }

    override fun onFatalError(t: Throwable) {
        Log.e(TAG, "Fatal audio capture error, stopping session", t)
        stopSelfCleanly()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_STOP = "com.jawtrack.app.action.STOP"
        const val EXTRA_CALIBRATION_ONLY = "calibration_only"

        private const val HEARTBEAT_INTERVAL_MILLIS = 60_000L
        private const val NOTIFICATION_TICK_MILLIS = 30_000L
        private const val LOW_BATTERY_STOP_THRESHOLD_PCT = 30
        private const val MAX_SESSION_DURATION_MILLIS = 12 * 60 * 60 * 1000L // 12h wakelock ceiling

        // -55dB is a conservative (over-sensitive) guess for a quiet bedroom, used only if a
        // session somehow starts with no RoomProfile yet -- the UI is supposed to prevent that.
        private const val FALLBACK_ENERGY_THRESHOLD_DB = -55.0

        /** Logged on every episode so old data stays interpretable after model/threshold changes (§12). */
        const val CLASSIFIER_VERSION = "gate3-heuristic-v1"

        private val _isRunning = MutableStateFlow(false)

        /**
         * True whenever the service process is alive. Cheap same-process signal (no bindService
         * round trip) that the UI uses to tell "actively recording" apart from "app relaunched
         * after the process died" — the latter is what the watchdog banner (§4.6.2) is for.
         */
        val isRunning: StateFlow<Boolean> = _isRunning

        fun start(context: Context, calibrationOnly: Boolean = false) {
            val intent = Intent(context, RecordingService::class.java)
                .putExtra(EXTRA_CALIBRATION_ONLY, calibrationOnly)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
