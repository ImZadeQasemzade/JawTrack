package com.jawtrack.app.ui.night

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jawtrack.app.JawTrackApp
import com.jawtrack.app.data.db.entities.Session
import com.jawtrack.app.health.HealthConnectAvailability
import com.jawtrack.app.health.HealthConnectRepo
import com.jawtrack.app.recording.OemBatteryAdvisor
import com.jawtrack.app.recording.RecordingService
import com.jawtrack.corelogic.watchdog.HeartbeatEvaluator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NightUiState(
    val isRecording: Boolean = false,
    val isCalibratingNow: Boolean = false,
    val elapsedMillis: Long = 0,
    val silentDeathAtMillis: Long? = null,
    val micPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val batteryOptimizationIgnored: Boolean = false,
    val isCharging: Boolean = false,
    /** No RoomProfile yet (§4.5): the only night this app will run is a calibration night. */
    val hasRoomProfile: Boolean = false,
    /** Health Connect (§5.1) is optional — recording works without it, enrichment just won't run. */
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionsGranted: Boolean = false
)

class NightViewModel(application: Application) : AndroidViewModel(application) {

    private val app: JawTrackApp get() = getApplication()

    private val _uiState = MutableStateFlow(NightUiState())
    val uiState: StateFlow<NightUiState> = _uiState.asStateFlow()

    private var activeSession: Session? = null

    /**
     * `refreshEnvironmentStatus()` was previously only called from `init` and
     * `MainActivity.onResume()` — plugging in the cable while the app stayed foregrounded (no
     * resume transition) left `isCharging` stuck at whatever it read on launch, permanently
     * blocking "Start night". Power-connect/disconnect broadcasts fire regardless of activity
     * lifecycle, so this catches that case live.
     */
    private val powerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshEnvironmentStatus()
        }
    }

    init {
        refreshEnvironmentStatus()
        ContextCompat.registerReceiver(
            getApplication(),
            powerStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        viewModelScope.launch { reconcileMostRecentSession() }
        viewModelScope.launch { refreshRoomProfileStatus() }
        viewModelScope.launch { refreshHealthConnectStatusSuspend() }
        viewModelScope.launch {
            var wasRecording = false
            RecordingService.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isRecording = running)
                if (running) {
                    startElapsedTicker()
                } else if (wasRecording) {
                    // A calibration night may have just finished and produced a fresh profile.
                    refreshRoomProfileStatus()
                }
                wasRecording = running
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(powerStateReceiver)
    }

    private suspend fun refreshRoomProfileStatus() {
        val hasProfile = app.sessionRepository.getMostRecentRoomProfile() != null
        _uiState.value = _uiState.value.copy(hasRoomProfile = hasProfile)
    }

    /** Also called after the Health Connect permission launcher returns, from the onboarding screen. */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch { refreshHealthConnectStatusSuspend() }
    }

    private suspend fun refreshHealthConnectStatusSuspend() {
        val repo = HealthConnectRepo(getApplication())
        val available = repo.availability() == HealthConnectAvailability.AVAILABLE
        val granted = if (available) repo.hasAllPermissions() else false
        _uiState.value = _uiState.value.copy(
            healthConnectAvailable = available,
            healthConnectPermissionsGranted = granted
        )
    }

    fun refreshEnvironmentStatus() {
        val context = getApplication<Application>()
        val micGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val charging = batteryManager?.isCharging ?: false

        _uiState.value = _uiState.value.copy(
            micPermissionGranted = micGranted,
            notificationPermissionGranted = notifGranted,
            batteryOptimizationIgnored = OemBatteryAdvisor.isIgnoringBatteryOptimizations(context),
            isCharging = charging
        )
    }

    /**
     * On launch, find every session left unterminated (RECORDING or CALIBRATION, never reached
     * a clean or error stop) whose service process is not currently alive — that's the
     * signature of an OEM battery kill (§4.6.2). Closes each one out as an error stop and
     * surfaces the banner.
     *
     * Deliberately checks *every* unterminated session, not just the most recent one: a
     * silently-killed session followed by even a short later session (a retry, a debug/test
     * tap) is no longer "most recent" by that narrower check, which let it sit unreconciled
     * forever — the later session masked it, and the banner never fired.
     */
    private suspend fun reconcileMostRecentSession() {
        activeSession = app.sessionRepository.getMostRecentSession()

        val now = System.currentTimeMillis()
        var latestSilentDeathAt: Long? = null

        for (session in app.sessionRepository.getUnterminatedSessions()) {
            val evaluation = HeartbeatEvaluator.evaluate(
                cleanShutdown = session.cleanShutdown,
                lastHeartbeatAtMillis = session.lastHeartbeatAt,
                referenceNowMillis = now
            )
            if (!evaluation.likelySilentDeath) continue // e.g. the genuinely still-running session, if any

            app.sessionRepository.endSession(
                sessionId = session.id,
                startedAt = session.startedAt,
                endedAt = evaluation.apparentStopAtMillis ?: session.lastHeartbeatAt ?: session.startedAt,
                cleanShutdown = false
            )
            val stopAt = evaluation.apparentStopAtMillis
            if (stopAt != null && (latestSilentDeathAt == null || stopAt > latestSilentDeathAt!!)) {
                latestSilentDeathAt = stopAt
            }
        }

        if (latestSilentDeathAt != null) {
            _uiState.value = _uiState.value.copy(silentDeathAtMillis = latestSilentDeathAt)
        }
    }

    fun dismissSilentDeathBanner() {
        _uiState.value = _uiState.value.copy(silentDeathAtMillis = null)
    }

    private fun startElapsedTicker() {
        viewModelScope.launch {
            val session = app.sessionRepository.getMostRecentSession() ?: return@launch
            _uiState.value = _uiState.value.copy(isCalibratingNow = session.calibrationOnly)
            while (RecordingService.isRunning.value) {
                _uiState.value = _uiState.value.copy(elapsedMillis = System.currentTimeMillis() - session.startedAt)
                delay(1_000)
            }
        }
    }

    fun startNight(calibrationOnly: Boolean = false) {
        RecordingService.start(getApplication(), calibrationOnly)
    }

    fun stopNight() {
        RecordingService.stop(getApplication())
    }

    /** "Delete all audio" (§6.7): every clip file and row, immediately, no undo. */
    fun deleteAllAudio() {
        viewModelScope.launch {
            app.clipRepository.deleteAllClips()
        }
    }
}
