package com.jawtrack.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jawtrack.app.data.db.entities.UserLabel
import com.jawtrack.corelogic.report.SpectrogramFrame
import kotlin.math.abs

/**
 * Screen 3 — episode detail + labeling (§8): waveform and spectrogram for one clip, playback,
 * and the confirm/reject/unsure loop the personalized model depends on. A swipe (in either
 * direction — direction doesn't matter here, there's no "back") or a label tap both advance to
 * the next unlabeled episode, per §8's "ten clips should take under a minute" goal.
 */
@Composable
fun EpisodeDetailScreen(
    state: EpisodeDetailUiState,
    onPlay: () -> Unit,
    onStopPlayback: () -> Unit,
    onLabel: (UserLabel) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    var dragAccumulatorPx by remember(state.episodeId) { mutableFloatStateOf(0f) }
    var swipeConsumed by remember(state.episodeId) { mutableFloatStateOf(0f) } // 0 = not yet fired this episode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .pointerInput(state.episodeId) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragAccumulatorPx = 0f },
                    onHorizontalDrag = { _, delta ->
                        dragAccumulatorPx += delta
                        // A swipe just moves to the next unlabeled clip without judging it —
                        // labeling itself always happens via an explicit button tap (§8's loop
                        // must never record a label the user didn't deliberately choose).
                        if (swipeConsumed == 0f && abs(dragAccumulatorPx) > SWIPE_THRESHOLD_PX) {
                            swipeConsumed = 1f
                            onSkip()
                        }
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Review episode", style = MaterialTheme.typography.headlineMedium)

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

            state.episodeId == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("All caught up — nothing left to review tonight.", style = MaterialTheme.typography.bodyLarge)
                }
            }

            else -> {
                Text(
                    "${state.remainingUnlabeledCount} left after this one",
                    style = MaterialTheme.typography.labelLarge
                )

                if (state.rejectedClasses.isNotEmpty()) {
                    Text(
                        "Nearby sounds Gate 2 saw: ${state.rejectedClasses.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                WaveformView(state.waveform)
                SpectrogramView(state.spectrogram)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPlay) { Text("Play") }
                    OutlinedButton(onClick = onStopPlayback) { Text("Stop") }
                }

                Text(
                    "Grinding shows a broadband smear; snoring shows a harmonic stack.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onLabel(UserLabel.CONFIRMED) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Grinding") }
                    Button(
                        onClick = { onLabel(UserLabel.REJECTED) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) { Text("Not grinding") }
                    OutlinedButton(onClick = { onLabel(UserLabel.UNSURE) }) { Text("Not sure") }
                }

                TextButton(onClick = onSkip) { Text("Skip for now") }
            }
        }

        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun WaveformView(waveform: FloatArray) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        if (waveform.isEmpty()) return@Canvas
        val columnWidth = size.width / waveform.size
        waveform.forEachIndexed { index, amplitude ->
            val barHeight = size.height * amplitude
            drawRect(
                color = Color(0xFF7FE0C1),
                topLeft = Offset(index * columnWidth, (size.height - barHeight) / 2f),
                size = Size(columnWidth * 0.8f, barHeight)
            )
        }
    }
}

@Composable
private fun SpectrogramView(frames: List<SpectrogramFrame>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (frames.isEmpty()) return@Canvas
        val bands = frames.first().bandDb.keys.sorted()
        if (bands.isEmpty()) return@Canvas

        val columnWidth = size.width / frames.size
        val rowHeight = size.height / bands.size

        val allDb = frames.flatMap { it.bandDb.values }
        val minDb = allDb.minOrNull() ?: 0.0
        val maxDb = (allDb.maxOrNull() ?: 1.0).coerceAtLeast(minDb + 1.0)

        frames.forEachIndexed { columnIndex, frame ->
            bands.forEachIndexed { bandIndex, band ->
                val db = frame.bandDb[band] ?: minDb
                val level = ((db - minDb) / (maxDb - minDb)).coerceIn(0.0, 1.0)
                drawRect(
                    color = Color(0xFF7FE0C1).copy(alpha = level.toFloat()),
                    topLeft = Offset(columnIndex * columnWidth, size.height - (bandIndex + 1) * rowHeight),
                    size = Size(columnWidth, rowHeight)
                )
            }
        }
    }
}

private const val SWIPE_THRESHOLD_PX = 120f
