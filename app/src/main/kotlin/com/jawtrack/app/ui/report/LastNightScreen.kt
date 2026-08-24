package com.jawtrack.app.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Screen 1 — Last night (§8): the JawTrack Index, confidence grade, trend vs. the 7-night
 * average, and a plain-language summary. Leads with a "stopped early" warning rather than the
 * index number when the service didn't shut down cleanly, so an incomplete night never reads
 * as a reassuringly low one.
 */
@Composable
fun LastNightScreen(
    state: ReportUiState,
    onViewTimeline: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Last night", style = MaterialTheme.typography.headlineMedium)

        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            !state.hasSession -> {
                Text(
                    "No recorded night yet. Start a night from the home screen to see a report here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> {
                if (state.serviceDiedEarly) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Recording stopped early", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tonight's numbers are incomplete — treat everything below as a " +
                                    "partial picture, not a full night's reading.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                IndexCard(state = state)

                Text(state.summarySentence, style = MaterialTheme.typography.bodyLarge)

                Button(onClick = onViewTimeline) { Text("View timeline") }

                Text(
                    "Wellness self-tracking, not a medical device. No diagnosis or treatment claims.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun IndexCard(state: ReportUiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("JawTrack Index", style = MaterialTheme.typography.titleMedium)
            Text(
                "%.1f".format(state.jawTrackIndex) + " episodes/hr",
                style = MaterialTheme.typography.displaySmall
            )
            ConfidenceBadge(grade = state.confidenceGrade)

            val delta = state.indexDelta
            Text(
                if (delta == null) {
                    "First night with a 7-night average to compare against."
                } else {
                    val direction = if (delta >= 0) "higher" else "lower"
                    "%.1f".format(kotlin.math.abs(delta)) + " $direction than your 7-night average"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ConfidenceBadge(grade: String) {
    val label = when (grade) {
        "A" -> "High confidence"
        "B" -> "Medium confidence"
        else -> "Low confidence"
    }
    Text("Grade $grade — $label", style = MaterialTheme.typography.labelLarge)
}
