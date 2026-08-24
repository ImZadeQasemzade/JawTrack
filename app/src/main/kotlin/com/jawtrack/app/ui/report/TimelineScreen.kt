package com.jawtrack.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Screen 2 — Timeline (§8): sleep-stage band, heart-rate line, episode ticks (height = peak
 * score), and grey hatching over gaps, all sharing [ReportUiState]'s fraction-of-night x-axis
 * from [com.jawtrack.corelogic.report.TimelineLayout]. Pinch to zoom, tap a tick for detail.
 */
@Composable
fun TimelineScreen(
    state: ReportUiState,
    onEpisodeTapped: (Long) -> Unit,
    onBack: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Timeline", style = MaterialTheme.typography.headlineMedium)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offsetX += pan.x
                    }
                }
                .pointerInput(state.episodeTicks, scale, offsetX) {
                    detectTapGestures { tapOffset ->
                        val width = size.width.toFloat()
                        val nearest = state.episodeTicks.minByOrNull { tick ->
                            abs(tick.fraction.toFloat() * width * scale + offsetX - tapOffset.x)
                        } ?: return@detectTapGestures
                        val nearestScreenX = nearest.fraction.toFloat() * width * scale + offsetX
                        if (abs(nearestScreenX - tapOffset.x) < TAP_TOLERANCE_PX) {
                            onEpisodeTapped(nearest.episodeId)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            withTransform({
                translate(left = offsetX)
                scale(scaleX = scale, scaleY = 1f, pivot = Offset.Zero)
            }) {
                state.stageBands.forEach { band ->
                    drawRect(
                        color = stageColor(band.stage),
                        topLeft = Offset((band.startFraction * width).toFloat(), 0f),
                        size = Size(((band.endFraction - band.startFraction) * width).toFloat(), height * 0.2f)
                    )
                }

                state.gapBands.forEach { gap ->
                    drawRect(
                        color = Color.Gray.copy(alpha = 0.4f),
                        topLeft = Offset((gap.startFraction * width).toFloat(), 0f),
                        size = Size(((gap.endFraction - gap.startFraction) * width).toFloat(), height)
                    )
                }

                if (state.heartRatePoints.size > 1) {
                    val maxBpm = state.heartRatePoints.maxOf { it.bpm }.coerceAtLeast(1)
                    val path = Path()
                    state.heartRatePoints.sortedBy { it.fraction }.forEachIndexed { index, point ->
                        val x = (point.fraction * width).toFloat()
                        val y = height * 0.3f + (1f - point.bpm.toFloat() / maxBpm) * height * 0.3f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = Color(0xFFFF6B6B), style = Stroke(width = 2f))
                }

                state.episodeTicks.forEach { tick ->
                    val x = (tick.fraction * width).toFloat()
                    val tickHeight = (height * 0.4f * tick.peakScoreFraction).toFloat()
                    drawLine(
                        color = Color(0xFF7FE0C1),
                        start = Offset(x, height * 0.7f),
                        end = Offset(x, height * 0.7f - tickHeight),
                        strokeWidth = 3f
                    )
                }
            }
        }

        Text(
            "Grey bands are gaps — audio wasn't captured then, so nothing could be detected there.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Pinch to zoom, tap a tick for episode detail.", style = MaterialTheme.typography.bodySmall)

        TextButton(onClick = onBack) { Text("Back") }
    }
}

private fun stageColor(stage: String): Color = when (stage) {
    "LIGHT" -> Color(0xFF3A5A78)
    "DEEP" -> Color(0xFF1B2E4A)
    "REM" -> Color(0xFF6A4C93)
    "AWAKE" -> Color(0xFF8C8C8C)
    "OUT_OF_BED" -> Color(0xFF5C5C5C)
    else -> Color(0xFF2A2A2A)
}

private const val TAP_TOLERANCE_PX = 40f
