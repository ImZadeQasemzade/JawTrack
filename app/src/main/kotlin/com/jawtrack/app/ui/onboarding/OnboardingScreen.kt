package com.jawtrack.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jawtrack.app.recording.OemBatteryAdvisor
import com.jawtrack.app.ui.night.NightUiState

/**
 * First-run setup (JawTrackSpec §4.2, §4.6.1, §6.8): microphone/notification permissions,
 * the battery-optimization exemption, OEM-specific autostart guidance, and the bed-partner
 * consent notice. A silently-killed overnight service is this app's worst failure mode, so
 * this flow front-loads everything that prevents it rather than leaving it to be discovered
 * after a wasted night.
 */
@Composable
fun OnboardingScreen(
    state: NightUiState,
    onRefreshStatus: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var consentAcknowledged by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshStatus() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshStatus() }

    val batteryExemptionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefreshStatus() }

    val oemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefreshStatus() }

    val oemGuidance = remember { OemBatteryAdvisor.guidanceFor(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Set up JawTrack", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A few one-time steps make sure recording actually survives the night.",
            style = MaterialTheme.typography.bodyMedium
        )

        SetupStep(
            title = "Microphone access",
            done = state.micPermissionGranted,
            body = "Required to listen for grinding sounds. Android will show a green mic " +
                "indicator all night while this runs — that's expected."
        ) {
            Button(onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Grant microphone")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SetupStep(
                title = "Notifications",
                done = state.notificationPermissionGranted,
                body = "Shows the persistent recording status so you can tell it's still running."
            ) {
                Button(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Grant notifications")
                }
            }
        }

        SetupStep(
            title = "Battery optimization",
            done = state.batteryOptimizationIgnored,
            body = "Android's Doze mode will otherwise throttle or kill the recording service " +
                "partway through the night."
        ) {
            Button(onClick = {
                batteryExemptionLauncher.launch(OemBatteryAdvisor.batteryOptimizationExemptionIntent(context))
            }) {
                Text("Disable battery optimization")
            }
        }

        SetupStep(
            title = "${oemGuidance.manufacturerLabel} background settings",
            done = false, // no reliable way to verify this OEM setting programmatically
            body = oemGuidance.steps.joinToString("\n") { "• $it" }
        ) {
            Button(onClick = {
                val intent = oemGuidance.oemSettingsIntent ?: OemBatteryAdvisor.appDetailsSettingsIntent(context)
                oemSettingsLauncher.launch(intent)
            }) {
                Text("Open settings")
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Before you start recording", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This app listens through the microphone all night. If someone else sleeps " +
                        "in the room, they will be recorded too. Only short clips around detected " +
                        "events are ever saved, encrypted, and auto-deleted after your chosen " +
                        "retention period — never the full night, and nothing ever leaves this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    Checkbox(checked = consentAcknowledged, onCheckedChange = { consentAcknowledged = it })
                    Text(
                        "I understand, and have informed anyone else in the room.",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        Button(
            onClick = onContinue,
            enabled = state.micPermissionGranted && consentAcknowledged
        ) {
            Text("Continue")
        }

        TextButton(onClick = onRefreshStatus) { Text("Refresh status") }
    }
}

@Composable
private fun SetupStep(title: String, done: Boolean, body: String, action: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (done) "✓ " else "○ ") + title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (!done) action()
        }
    }
}
