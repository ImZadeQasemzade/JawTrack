package com.jawtrack.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jawtrack.app.ui.theme.JawTrackTheme

/**
 * Health Connect refuses to show its permission dialog unless the requesting app can show a
 * privacy rationale (§5.1: "A privacy-policy rationale activity... is required or permission
 * requests are rejected"). JawTrack has no hosted privacy policy — no server, no website, the
 * same "nothing leaves the device" promise the rest of the app is built around — so this states
 * the commitment in-app instead of linking out to a page that doesn't exist.
 *
 * Unverified: the exact manifest wiring Health Connect expects for this activity (the intent
 * action, and the separate activity-alias for the system Permissions/Usage screen) has changed
 * across `connect-client` versions as Health Connect moved from a standalone app into AOSP —
 * check `AndroidManifest.xml`'s entries against the current docs before shipping.
 */
class HealthConnectRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JawTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("How JawTrack uses Health Connect", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "JawTrack reads heart rate, heart rate variability, sleep stages, and " +
                                "respiratory rate that Garmin Connect syncs into Health Connect, to " +
                                "show which sleep stage your grinding episodes fell in and how your " +
                                "night's heart rate looked overall.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "This access is read-only. The data stays on this device and is never " +
                                "uploaded anywhere — JawTrack has no server and requests no internet " +
                                "permission at all.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Heart rate from a watch sync is too coarse to confirm a single grinding " +
                                "episode second-by-second, so it's used at the night and cluster " +
                                "level only — never claimed as per-episode precision.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
