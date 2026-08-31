package com.jawtrack.app.ui.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun StartNightScreen(
    state: NightUiState,
    onStartNight: (calibrationOnly: Boolean) -> Unit,
    onStopNight: () -> Unit,
    onDismissSilentDeathBanner: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onDeleteAllAudio: () -> Unit,
    onViewReport: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("JawTrack", style = MaterialTheme.typography.headlineMedium)

        if (state.silentDeathAtMillis != null) {
            SilentDeathBanner(
                atMillis = state.silentDeathAtMillis,
                onDismiss = onDismissSilentDeathBanner,
                onOpenSettings = onOpenOnboarding
            )
        }

        if (state.isRecording) {
            RecordingCard(
                elapsedMillis = state.elapsedMillis,
                isCalibratingNow = state.isCalibratingNow,
                onStopNight = onStopNight
            )
        } else {
            ReadyCard(state = state, onStartNight = onStartNight, onOpenOnboarding = onOpenOnboarding)
            TextButton(onClick = onViewReport) {
                Text("Last night's report")
            }
            TextButton(onClick = { showDeleteConfirmation = true }) {
                Text("Delete all audio")
            }
        }

        Text(
            "Wellness self-tracking, not a medical device. No diagnosis or treatment claims.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete all audio?") },
            text = { Text("Every saved clip is deleted immediately. This can't be undone (§6.7).") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAllAudio()
                    showDeleteConfirmation = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SilentDeathBanner(atMillis: Long, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Last night's recording stopped early, at ${formatClockTime(atMillis)}.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "This is usually battery optimization killing the app overnight. " +
                    "Check the background-activity settings for your phone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpenSettings) { Text("Fix settings") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun RecordingCard(elapsedMillis: Long, isCalibratingNow: Boolean, onStopNight: () -> Unit) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (isCalibratingNow) "Calibrating your room" else "Listening", style = MaterialTheme.typography.titleLarge)
            if (isCalibratingNow) {
                Text(
                    "Measuring the room's background noise. No detection runs tonight — just leave it be.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(formatElapsed(elapsedMillis), style = MaterialTheme.typography.displaySmall)
            Button(onClick = onStopNight) { Text("Stop night") }
        }
    }
}

@Composable
private fun ReadyCard(state: NightUiState, onStartNight: (Boolean) -> Unit, onOpenOnboarding: () -> Unit) {
    // Charging is a strong recommendation (§4.6.3), not a hard gate: some OEMs (e.g. Motorola's
    // Adaptive Charging) pause active charging while plugged in to protect battery health, which
    // makes BatteryManager.isCharging() report false even though the phone is genuinely on the
    // charger — blocking "Start night" on that signal was locking people out overnight for a
    // false negative.
    val canStart = state.micPermissionGranted

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ready for tonight", style = MaterialTheme.typography.titleLarge)

            ChecklistLine("Microphone permission", state.micPermissionGranted)
            ChecklistLine("Notification permission", state.notificationPermissionGranted)
            ChecklistLine("Battery optimization disabled", state.batteryOptimizationIgnored)
            ChecklistLine("Phone is charging (recommended)", state.isCharging)

            if (!state.micPermissionGranted || !state.batteryOptimizationIgnored) {
                TextButton(onClick = onOpenOnboarding) { Text("Fix setup") }
            }

            if (!state.hasRoomProfile) {
                Text(
                    "First night: JawTrack needs to measure your room's background noise " +
                        "before it can tell grinding apart from everything else. No detection " +
                        "runs tonight — just leave the phone in place overnight (§4.5).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { onStartNight(true) }, enabled = canStart) {
                    Text("Start calibration night")
                }
            } else {
                Button(onClick = { onStartNight(false) }, enabled = canStart) {
                    Text("Start night")
                }
                TextButton(onClick = { onStartNight(true) }, enabled = canStart) {
                    Text("Recalibrate room (new room, travel)")
                }
            }

            if (!state.isCharging) {
                Text(
                    "Recommended: plug in your phone before starting — an 8h recording drains " +
                        "the battery fast (§4.6.3). If it's already on the charger and this still " +
                        "shows unticked, some phones (e.g. Motorola's Adaptive Charging) pause " +
                        "active charging to protect the battery — that's fine, go ahead and start.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ChecklistLine(label: String, done: Boolean) {
    Text((if (done) "✓ " else "○ ") + label, style = MaterialTheme.typography.bodyMedium)
}

private fun formatElapsed(elapsedMillis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
    return "%dh %02dm".format(hours, minutes)
}

private fun formatClockTime(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
