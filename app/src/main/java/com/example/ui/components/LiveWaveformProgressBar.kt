package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioVisualizerManager
import kotlin.math.abs

/**
 * Live audio-reactive waveform progress bar matching Image 1:
 * - Vertical bars for played (warm translucent orange) and unplayed (crisp white) audio waves.
 * - Solid continuous center horizontal progress line (vibrant orange for played, translucent white for unplayed).
 * - Circular orange playhead knob at the center boundary.
 * - Smooth tap and drag seek interaction.
 */
@Composable
fun LiveWaveformProgressBar(
    songId: String,
    songDuration: Long,
    currentProgressMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    playedBarColor: Color = Color(0xFFFF6A00).copy(alpha = 0.72f),
    unplayedBarColor: Color = Color.White.copy(alpha = 0.82f),
    centerLinePlayedColor: Color = Color(0xFFFF6A00),
    centerLineUnplayedColor: Color = Color.White.copy(alpha = 0.40f),
    playheadColor: Color = Color(0xFFFF6A00),
    timeTextColor: Color = Color(0xFFE2E2EC),
    showRemainingTime: Boolean = false,
    onToggleRemainingTime: () -> Unit = {}
) {
    // Number of vertical bars across the canvas (46 bars gives the exact spacing and balance from Image 1)
    val barCount = 46

    // Base waveform profile seeded per song for consistent acoustic fingerprint
    val songSeed = remember(songId) {
        val hash = songId.hashCode().toLong()
        if (hash != 0L) abs(hash) else 42L
    }
    val baseProfile = remember(songSeed) {
        AudioVisualizerManager.generateBaseWaveformProfile(songSeed, barCount)
    }

    // Dynamic scrubbing state (0.0f..1.0f)
    var localScrubFraction by remember { mutableStateOf<Float?>(null) }

    // Effective playback fraction
    val currentFraction = localScrubFraction ?: if (songDuration > 0) {
        (currentProgressMs.toFloat() / songDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Current displayed time
    val effectiveProgressMs = if (localScrubFraction != null) {
        (localScrubFraction!! * songDuration).toLong().coerceIn(0L, songDuration)
    } else {
        currentProgressMs.coerceIn(0L, songDuration)
    }

    // 60 FPS live ticker when playing
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, songId) {
        if (isPlaying) {
            while (true) {
                withFrameNanos { nanos ->
                    frameNanos = nanos
                }
            }
        }
    }

    // Compute live dynamic amplitudes for the vertical bars
    val dynamicAmplitudes = remember(frameNanos, baseProfile, effectiveProgressMs, isPlaying) {
        AudioVisualizerManager.computeFrameAmplitudes(
            baseProfile = baseProfile,
            positionMs = effectiveProgressMs,
            durationMs = songDuration,
            isPlaying = isPlaying,
            elapsedTimeNanos = frameNanos
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_waveform_progress_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Live Waveform Canvas with Central Progress Stroke
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 6.dp)
                .pointerInput(songDuration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (size.width > 0) {
                            val tapFraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            localScrubFraction = tapFraction
                        }
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && change.pressed) {
                                if (size.width > 0) {
                                    val dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    localScrubFraction = dragFraction
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        localScrubFraction?.let { fraction ->
                            onSeek((fraction * songDuration).toLong())
                        }
                        localScrubFraction = null
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f

            if (canvasWidth <= 0f) return@Canvas

            val totalStep = canvasWidth / barCount.toFloat()
            val barWidth = 2.8.dp.toPx()
            val maxHeight = canvasHeight * 0.90f
            val minHeight = 6.dp.toPx()

            val progressX = (currentFraction * canvasWidth).coerceIn(0f, canvasWidth)

            // --- LAYER 1: Draw Vertical Waveform Bars ---
            for (i in 0 until barCount) {
                val barCenterX = (i + 0.5f) * totalStep
                val amplitude = dynamicAmplitudes.getOrElse(i) { 0.35f }
                val barHeight = (minHeight + amplitude * (maxHeight - minHeight)).coerceIn(minHeight, maxHeight)
                val halfBarHeight = barHeight / 2f

                val isPlayed = barCenterX <= progressX

                val color = if (isPlayed) playedBarColor else unplayedBarColor

                drawLine(
                    color = color,
                    start = Offset(barCenterX, centerY - halfBarHeight),
                    end = Offset(barCenterX, centerY + halfBarHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }

            // --- LAYER 2: Draw Continuous Center Horizontal Progress Stroke ---
            val centerStrokeWidth = 3.6.dp.toPx()
            val startX = (totalStep * 0.4f).coerceAtLeast(0f)
            val endX = (canvasWidth - totalStep * 0.4f).coerceIn(0f, canvasWidth)

            // 2a. Unplayed portion of center line (from progressX to end)
            if (progressX < endX) {
                drawLine(
                    color = centerLineUnplayedColor,
                    start = Offset(maxOf(startX, progressX), centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = centerStrokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // 2b. Played portion of center line (from startX to progressX)
            if (progressX > startX) {
                drawLine(
                    color = centerLinePlayedColor,
                    start = Offset(startX, centerY),
                    end = Offset(progressX, centerY),
                    strokeWidth = centerStrokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // --- LAYER 3: Draw Playhead Circular Knob ---
            val knobRadius = 5.2.dp.toPx()
            // Ambient outer glow
            drawCircle(
                color = playheadColor.copy(alpha = 0.35f),
                radius = knobRadius + 3.dp.toPx(),
                center = Offset(progressX, centerY)
            )
            // Main solid vibrant orange circle
            drawCircle(
                color = playheadColor,
                radius = knobRadius,
                center = Offset(progressX, centerY)
            )
            // Crisp center accent dot
            drawCircle(
                color = Color(0xFFFFE0B2),
                radius = 2.dp.toPx(),
                center = Offset(progressX, centerY)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Formatted Timestamps Row (01:41 vs 03:35)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDurationMs(effectiveProgressMs),
                color = timeTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (showRemainingTime) {
                    val remain = maxOf(0L, songDuration - effectiveProgressMs)
                    "-${formatDurationMs(remain)}"
                } else {
                    formatDurationMs(songDuration)
                },
                color = timeTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onToggleRemainingTime() }
            )
        }
    }
}

private fun formatDurationMs(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
