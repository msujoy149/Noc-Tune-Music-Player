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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioVisualizerManager
import kotlin.math.abs
import kotlin.math.max

/**
 * Live audio-reactive waveform progress bar that combines
 * real-time audio visualization, playback progress, and interactive seeking into ONE unified component.
 */
@Composable
fun LiveWaveformProgressBar(
    songId: String,
    songDuration: Long,
    currentProgressMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    playedColor: Color = Color(0xFFFF6A00),       // Vibrant orange matching reference
    unplayedColor: Color = Color(0xFFE6E6EC),     // Light gray / white for remaining
    playheadColor: Color = Color(0xFFFF6A00),     // Center division playhead
    timeTextColor: Color = Color(0xFFC4C4D0),
    showRemainingTime: Boolean = false,
    onToggleRemainingTime: () -> Unit = {}
) {
    // Number of vertical bars across the canvas
    val barCount = 58

    // Base waveform profile seeded per song for consistent studio track mastering landscape
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

    // Dynamic animation frame ticker for 60fps live audio-reactive waveform
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

    // Compute live audio-reactive bar amplitudes
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
        // 1. Live Waveform Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp)
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

            val totalBarAndSpacing = canvasWidth / barCount.toFloat()
            val barWidth = max(2.5f, totalBarAndSpacing * 0.58f)
            val maxHeight = canvasHeight * 0.88f
            val minHeight = 4.dp.toPx()

            val progressX = (currentFraction * canvasWidth).coerceIn(0f, canvasWidth)

            // Draw all vertical waveform bars
            for (i in 0 until barCount) {
                val barCenterX = (i + 0.5f) * totalBarAndSpacing
                val amplitude = dynamicAmplitudes.getOrElse(i) { 0.3f }
                val barHeight = (minHeight + amplitude * (maxHeight - minHeight)).coerceIn(minHeight, maxHeight)
                val halfBarHeight = barHeight / 2f

                val isPlayed = barCenterX <= progressX

                val barColor = if (isPlayed) {
                    playedColor
                } else {
                    unplayedColor.copy(alpha = 0.88f)
                }

                drawLine(
                    color = barColor,
                    start = Offset(barCenterX, centerY - halfBarHeight),
                    end = Offset(barCenterX, centerY + halfBarHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }

            // Draw clean playhead division indicator at the exact boundary
            if (canvasWidth > 0f) {
                val playheadRadius = 4.5.dp.toPx()
                // Outer glow / accent
                drawCircle(
                    color = playedColor.copy(alpha = 0.35f),
                    radius = playheadRadius + 3.dp.toPx(),
                    center = Offset(progressX, centerY)
                )
                // Main pin
                drawCircle(
                    color = playedColor,
                    radius = playheadRadius,
                    center = Offset(progressX, centerY)
                )
                // Crisp center core
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(progressX, centerY)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Formatted Timestamps Row (Elapsed vs Total Duration)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDurationMs(effectiveProgressMs),
                color = timeTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
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
                fontWeight = FontWeight.SemiBold,
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
