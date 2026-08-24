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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jawtrack.app.health.EnrichmentWorker
import com.jawtrack.app.ui.OnboardingPrefs
import com.jawtrack.app.ui.night.NightViewModel
import com.jawtrack.app.ui.night.StartNightScreen
import com.jawtrack.app.ui.onboarding.OnboardingScreen
import com.jawtrack.app.ui.report.EpisodeDetailScreen
import com.jawtrack.app.ui.report.EpisodeDetailViewModel
import com.jawtrack.app.ui.report.LastNightScreen
import com.jawtrack.app.ui.report.ReportViewModel
import com.jawtrack.app.ui.report.TimelineScreen
import com.jawtrack.app.ui.theme.JawTrackTheme

/** Where the report flow (§8 Screens 1-3) currently is, layered on top of [StartNightScreen]. */
private sealed class ReportDestination {
    object LastNight : ReportDestination()
    object Timeline : ReportDestination()
    data class Detail(val episodeId: Long) : ReportDestination()
}

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
                    var reportDestination by remember { mutableStateOf<ReportDestination?>(null) }
                    val state by viewModel.uiState.collectAsState()

                    LaunchedEffect(Unit) { viewModel.refreshEnvironmentStatus() }

                    val destination = reportDestination
                    when {
                        showOnboarding -> OnboardingScreen(
                            state = state,
                            onRefreshStatus = { viewModel.refreshEnvironmentStatus() },
                            onContinue = {
                                OnboardingPrefs.setCompleted(this)
                                showOnboarding = false
                            },
                            onHealthConnectStatusChanged = { viewModel.refreshHealthConnectStatus() }
                        )

                        destination == null -> StartNightScreen(
                            state = state,
                            onStartNight = { calibrationOnly -> viewModel.startNight(calibrationOnly) },
                            onStopNight = { viewModel.stopNight() },
                            onDismissSilentDeathBanner = { viewModel.dismissSilentDeathBanner() },
                            onOpenOnboarding = { showOnboarding = true },
                            onDeleteAllAudio = { viewModel.deleteAllAudio() },
                            onViewReport = { reportDestination = ReportDestination.LastNight }
                        )

                        destination is ReportDestination.LastNight -> {
                            val reportViewModel: ReportViewModel = viewModel()
                            val reportState by reportViewModel.uiState.collectAsState()
                            LastNightScreen(
                                state = reportState,
                                onViewTimeline = { reportDestination = ReportDestination.Timeline },
                                onBack = { reportDestination = null }
                            )
                        }

                        destination is ReportDestination.Timeline -> {
                            val reportViewModel: ReportViewModel = viewModel()
                            val reportState by reportViewModel.uiState.collectAsState()
                            TimelineScreen(
                                state = reportState,
                                onEpisodeTapped = { episodeId -> reportDestination = ReportDestination.Detail(episodeId) },
                                onBack = { reportDestination = ReportDestination.LastNight }
                            )
                        }

                        destination is ReportDestination.Detail -> {
                            val reportViewModel: ReportViewModel = viewModel()
                            val reportState by reportViewModel.uiState.collectAsState()
                            // Keyed by the tapped episode id so a fresh instance backs each entry into Screen 3 —
                            // otherwise the Activity-scoped default would reuse a `started` (and thus inert) instance
                            // from a previous episode.
                            val detailViewModel: EpisodeDetailViewModel = viewModel(key = "episode-detail-${destination.episodeId}")
                            val detailState by detailViewModel.uiState.collectAsState()

                            LaunchedEffect(destination.episodeId) {
                                reportState.sessionId?.let { sessionId ->
                                    detailViewModel.start(sessionId, destination.episodeId)
                                }
                            }

                            EpisodeDetailScreen(
                                state = detailState,
                                onPlay = { detailViewModel.play() },
                                onStopPlayback = { detailViewModel.stopPlayback() },
                                onLabel = { label -> detailViewModel.label(label) },
                                onSkip = { detailViewModel.skip() },
                                onBack = { reportDestination = ReportDestination.Timeline }
                            )
                        }
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
