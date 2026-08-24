package com.jawtrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jawtrack.app.health.EnrichmentWorker
import com.jawtrack.app.ui.OnboardingPrefs
import com.jawtrack.app.ui.night.NightViewModel
import com.jawtrack.app.ui.night.StartNightScreen
import com.jawtrack.app.ui.onboarding.OnboardingScreen
import com.jawtrack.app.ui.theme.JawTrackTheme

/**
 * Single-activity host. Recording must be started from a visible Activity (§4.2, API 34+
 * background-start restriction on microphone foreground services), so "Start night" always
 * lives here rather than behind a scheduled trigger.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: NightViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JawTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showOnboarding by remember { mutableStateOf(!OnboardingPrefs.isCompleted(this)) }
                    val state by viewModel.uiState.collectAsState()

                    LaunchedEffect(Unit) { viewModel.refreshEnvironmentStatus() }

                    if (showOnboarding) {
                        OnboardingScreen(
                            state = state,
                            onRefreshStatus = { viewModel.refreshEnvironmentStatus() },
                            onContinue = {
                                OnboardingPrefs.setCompleted(this)
                                showOnboarding = false
                            },
                            onHealthConnectStatusChanged = { viewModel.refreshHealthConnectStatus() }
                        )
                    } else {
                        StartNightScreen(
                            state = state,
                            onStartNight = { calibrationOnly -> viewModel.startNight(calibrationOnly) },
                            onStopNight = { viewModel.stopNight() },
                            onDismissSilentDeathBanner = { viewModel.dismissSilentDeathBanner() },
                            onOpenOnboarding = { showOnboarding = true },
                            onDeleteAllAudio = { viewModel.deleteAllAudio() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironmentStatus()
        EnrichmentWorker.enqueue(this) // §5.3: "enqueued on app foreground in the morning"
    }
}
