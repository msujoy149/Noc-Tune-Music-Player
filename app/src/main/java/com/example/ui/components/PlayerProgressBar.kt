package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioVisualizerManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unified Player Progress Bar supporting 5 distinct visual styles:
 * 1. ORIGINAL: Classic Sleek Seeker Slider (Default)
 * 2. DYNAMIC_WAVEFORM: Audio-Reactive Vertical Waveform Bars
 * 3. DYNAMIC_PULSE_BARS: Segmented Equalizer Pulse Bars
 * 4. SMOOTH_WAVE_LINE: Fluid Audio-Reactive Undulating Sine Wave
 * 5. MINIMAL_AUDIO_BARS: Modern Minimalist Soundwave Spectrum
 */
@Composable
fun PlayerProgressBar(
    style: ProgressBarStyle,
    songId: String,
    songDuration: Long,
    currentProgressMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    colorConfig: ProgressBarColorConfig = ProgressBarColorConfig(),
    unplayedColor: Color = Color(0xFF1B1728),
    timeTextColor: Color = Color(0xFF8B8599),
    showRemainingTime: Boolean = false,
    onToggleRemainingTime: () -> Unit = {}
) {
    val playedColor = colorConfig.getEffectiveColor(style)

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

    // 60 FPS live frame ticker for audio-reactive styles
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

    // Base waveform profile seeded per song for acoustic stability
    val songSeed = remember(songId) {
        val hash = songId.hashCode().toLong()
        if (hash != 0L) abs(hash) else 42L
    }
    val baseProfile = remember(songSeed) {
        AudioVisualizerManager.generateBaseWaveformProfile(songSeed, 48)
    }

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
            .testTag("player_progress_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Seeker Canvas Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
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
            Crossfade(
                targetState = style,
                animationSpec = tween(250),
                label = "ProgressBarStyleCrossfade"
            ) { targetStyle ->
                when (targetStyle) {
                    ProgressBarStyle.ORIGINAL -> {
                        OriginalProgressBarCanvas(
                            fraction = currentFraction,
                            playedColor = playedColor,
                            unplayedColor = unplayedColor,
                            indicatorColor = playedColor
                        )
                    }
                    ProgressBarStyle.DYNAMIC_WAVEFORM -> {
                        DynamicWaveformProgressBarCanvas(
                            fraction = currentFraction,
                            amplitudes = dynamicAmplitudes,
                            playedBarColor = playedColor.copy(alpha = 0.85f),
                            unplayedBarColor = Color.White.copy(alpha = 0.75f),
                            centerLinePlayedColor = playedColor,
                            centerLineUnplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = playedColor
                        )
                    }
                    ProgressBarStyle.DYNAMIC_PULSE_BARS -> {
                        DynamicPulseBarsProgressBarCanvas(
                            fraction = currentFraction,
                            amplitudes = dynamicAmplitudes,
                            playedColor = playedColor,
                            unplayedColor = Color.White.copy(alpha = 0.75f),
                            playheadColor = playedColor
                        )
                    }
                    ProgressBarStyle.SMOOTH_WAVE_LINE -> {
                        SmoothWaveLineProgressBarCanvas(
                            fraction = currentFraction,
                            amplitudes = dynamicAmplitudes,
                            frameNanos = frameNanos,
                            isPlaying = isPlaying,
                            playedColor = playedColor,
                            unplayedColor = Color.White.copy(alpha = 0.75f),
                            playheadColor = playedColor
                        )
                    }
                    ProgressBarStyle.MINIMAL_AUDIO_BARS -> {
                        MinimalAudioBarsProgressBarCanvas(
                            fraction = currentFraction,
                            amplitudes = dynamicAmplitudes,
                            playedColor = playedColor,
                            unplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = playedColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Formatted Timestamps Row
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

// ==========================================
// STYLE 1: ORIGINAL (Classic Rounded Pill with Vertical Marker)
// Exactly matching original Noc Tune design in screenshot
// ==========================================
@Composable
fun OriginalProgressBarCanvas(
    fraction: Float,
    playedColor: Color = Color(0xFF6B4EE0),
    unplayedColor: Color = Color(0xFF1B1728),
    indicatorColor: Color = Color(0xFF6B4EE0),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val trackHeight = 10.dp.toPx()
        val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        val progressX = (fraction * width).coerceIn(0f, width)

        // 1. Full Unplayed Background Pill Track
        drawRoundRect(
            color = unplayedColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(width, trackHeight),
            cornerRadius = cornerRadius
        )

        // 2. Played Section Pill Track (Vibrant Purple matching screenshot)
        if (progressX > 0f) {
            drawRoundRect(
                color = playedColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(progressX, trackHeight),
                cornerRadius = cornerRadius
            )
        }

        // 3. Subtle Dot at the far right end of the track
        val endDotRadius = 2.dp.toPx()
        drawCircle(
            color = indicatorColor.copy(alpha = 0.5f),
            radius = endDotRadius,
            center = Offset(width - trackHeight / 2f, centerY)
        )

        // 4. Sleek Vertical Indicator Bar / Thumb dividing the played and unplayed track (as in screenshot)
        val indicatorWidth = 3.5.dp.toPx()
        val indicatorHeight = 24.dp.toPx()
        drawRoundRect(
            color = indicatorColor,
            topLeft = Offset(progressX - indicatorWidth / 2f, centerY - indicatorHeight / 2f),
            size = Size(indicatorWidth, indicatorHeight),
            cornerRadius = CornerRadius(indicatorWidth / 2f, indicatorWidth / 2f)
        )
    }
}

// ==========================================
// STYLE 2: DYNAMIC WAVEFORM
// ==========================================
@Composable
fun DynamicWaveformProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    playedBarColor: Color,
    unplayedBarColor: Color,
    centerLinePlayedColor: Color,
    centerLineUnplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 46
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f
        if (canvasWidth <= 0f) return@Canvas

        val totalStep = canvasWidth / barCount.toFloat()
        val barWidth = 2.8.dp.toPx()
        val maxHeight = canvasHeight * 0.90f
        val minHeight = 6.dp.toPx()
        val progressX = (fraction * canvasWidth).coerceIn(0f, canvasWidth)

        // Layer 1: Vertical Waveform Bars
        for (i in 0 until barCount) {
            val barCenterX = (i + 0.5f) * totalStep
            val amplitude = amplitudes.getOrElse(i) { 0.35f }
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

        // Layer 2: Center Line
        val centerStrokeWidth = 3.6.dp.toPx()
        val startX = (totalStep * 0.4f).coerceAtLeast(0f)
        val endX = (canvasWidth - totalStep * 0.4f).coerceIn(0f, canvasWidth)

        if (progressX < endX) {
            drawLine(
                color = centerLineUnplayedColor,
                start = Offset(maxOf(startX, progressX), centerY),
                end = Offset(endX, centerY),
                strokeWidth = centerStrokeWidth,
                cap = StrokeCap.Round
            )
        }
        if (progressX > startX) {
            drawLine(
                color = centerLinePlayedColor,
                start = Offset(startX, centerY),
                end = Offset(progressX, centerY),
                strokeWidth = centerStrokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Layer 3: Playhead Knob
        val knobRadius = 5.2.dp.toPx()
        drawCircle(
            color = playheadColor.copy(alpha = 0.35f),
            radius = knobRadius + 3.dp.toPx(),
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = playheadColor,
            radius = knobRadius,
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = Color(0xFFFFE0B2),
            radius = 2.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}

// ==========================================
// STYLE 3: DYNAMIC PULSE BARS (Segmented Equalizer)
// ==========================================
@Composable
fun DynamicPulseBarsProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 38
    val maxSegmentsPerBar = 7

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val step = width / barCount.toFloat()
        val barWidth = 3.6.dp.toPx()
        val segmentHeight = 4.5.dp.toPx()
        val segmentGap = 2.dp.toPx()
        val totalSegmentSpan = segmentHeight + segmentGap

        for (i in 0 until barCount) {
            val barCenterX = (i + 0.5f) * step
            val amp = amplitudes.getOrElse(i) { 0.35f }
            val activeSegments = (amp * maxSegmentsPerBar).toInt().coerceIn(1, maxSegmentsPerBar)
            val isPlayed = barCenterX <= progressX

            for (s in 0 until maxSegmentsPerBar) {
                val isSegmentLit = s < activeSegments
                val offsetFromCenter = (s + 0.5f) * totalSegmentSpan

                val segmentColor = if (isPlayed) {
                    if (isSegmentLit) {
                        val heat = s.toFloat() / maxSegmentsPerBar.toFloat()
                        if (heat > 0.6f) Color(0xFFFF3D00) else playedColor
                    } else {
                        playedColor.copy(alpha = 0.20f)
                    }
                } else {
                    if (isSegmentLit) {
                        unplayedColor.copy(alpha = 0.85f)
                    } else {
                        unplayedColor.copy(alpha = 0.18f)
                    }
                }

                // Upper mirror segment
                drawRoundRect(
                    color = segmentColor,
                    topLeft = Offset(barCenterX - barWidth / 2f, centerY - offsetFromCenter - segmentHeight / 2f),
                    size = Size(barWidth, segmentHeight),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )
                // Lower mirror segment
                drawRoundRect(
                    color = segmentColor,
                    topLeft = Offset(barCenterX - barWidth / 2f, centerY + offsetFromCenter - segmentHeight / 2f),
                    size = Size(barWidth, segmentHeight),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )
            }
        }

        // Distinct Neon Playhead Cursor
        val cursorWidth = 2.5.dp.toPx()
        val cursorHeight = height * 0.85f
        drawLine(
            color = playheadColor.copy(alpha = 0.4f),
            start = Offset(progressX, centerY - cursorHeight / 2f),
            end = Offset(progressX, centerY + cursorHeight / 2f),
            strokeWidth = cursorWidth + 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = playheadColor,
            start = Offset(progressX, centerY - cursorHeight / 2f),
            end = Offset(progressX, centerY + cursorHeight / 2f),
            strokeWidth = cursorWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}

// ==========================================
// STYLE 4: SMOOTH WAVE LINE (Continuous Sine Wave)
// ==========================================
@Composable
fun SmoothWaveLineProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    frameNanos: Long,
    isPlaying: Boolean,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val timeSec = frameNanos / 1_000_000_000.0
        val wavePhase = if (isPlaying) (timeSec * 3.5).toFloat() else 0f
        val maxWaveAmp = height * 0.38f

        val pointsCount = 120
        val wavePoints = ArrayList<Offset>(pointsCount)

        for (p in 0..pointsCount) {
            val px = (p.toFloat() / pointsCount.toFloat()) * width
            val normX = px / width
            val ampIndex = (normX * (amplitudes.size - 1)).toInt().coerceIn(0, amplitudes.size - 1)
            val dynamicScale = amplitudes.getOrElse(ampIndex) { 0.4f }

            val harmonic1 = sin(normX * Math.PI * 5.0 + wavePhase).toFloat()
            val harmonic2 = cos(normX * Math.PI * 9.0 - wavePhase * 1.3f).toFloat() * 0.45f
            val waveY = centerY + (harmonic1 + harmonic2) * (maxWaveAmp * dynamicScale * 0.7f)
            wavePoints.add(Offset(px, waveY))
        }

        // Construct played path & unplayed path
        val playedPath = Path()
        val unplayedPath = Path()
        val playedFillPath = Path()

        var playheadY = centerY
        var foundPlayhead = false

        wavePoints.forEachIndexed { index, pt ->
            if (index == 0) {
                playedPath.moveTo(pt.x, pt.y)
                playedFillPath.moveTo(pt.x, centerY)
                playedFillPath.lineTo(pt.x, pt.y)
            }

            if (pt.x <= progressX) {
                if (index > 0) {
                    playedPath.lineTo(pt.x, pt.y)
                    playedFillPath.lineTo(pt.x, pt.y)
                }
                playheadY = pt.y
            } else {
                if (!foundPlayhead) {
                    foundPlayhead = true
                    // Interpolate exact playhead point
                    val prev = wavePoints.getOrElse(index - 1) { pt }
                    val t = if (pt.x != prev.x) ((progressX - prev.x) / (pt.x - prev.x)).coerceIn(0f, 1f) else 0f
                    playheadY = prev.y + (pt.y - prev.y) * t

                    playedPath.lineTo(progressX, playheadY)
                    playedFillPath.lineTo(progressX, playheadY)
                    playedFillPath.lineTo(progressX, centerY)
                    playedFillPath.close()

                    unplayedPath.moveTo(progressX, playheadY)
                }
                unplayedPath.lineTo(pt.x, pt.y)
            }
        }

        if (!foundPlayhead) {
            playedFillPath.lineTo(progressX, centerY)
            playedFillPath.close()
        }

        // Draw Ambient Glow Gradient Fill under played wave
        if (progressX > 0f) {
            drawPath(
                path = playedFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(playedColor.copy(alpha = 0.28f), Color.Transparent),
                    startY = centerY - maxWaveAmp,
                    endY = centerY + maxWaveAmp
                )
            )
        }

        // Draw Unplayed Wave Stroke
        drawPath(
            path = unplayedPath,
            color = unplayedColor.copy(alpha = 0.35f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Played Wave Stroke
        drawPath(
            path = playedPath,
            brush = Brush.horizontalGradient(
                colors = listOf(playedColor.copy(alpha = 0.85f), Color(0xFFFF3D00)),
                startX = 0f,
                endX = progressX.coerceAtLeast(1f)
            ),
            style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Playhead Pearl riding the wave
        val pearlRadius = 6.dp.toPx()
        drawCircle(
            color = playheadColor.copy(alpha = 0.35f),
            radius = pearlRadius + 4.dp.toPx(),
            center = Offset(progressX, playheadY)
        )
        drawCircle(
            color = playheadColor,
            radius = pearlRadius,
            center = Offset(progressX, playheadY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = Offset(progressX, playheadY)
        )
    }
}

// ==========================================
// STYLE 5: MINIMAL AUDIO BARS (Modern Soundwave Spectrum)
// ==========================================
@Composable
fun MinimalAudioBarsProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 54

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val step = width / barCount.toFloat()
        val barWidth = 2.2.dp.toPx()
        val maxHeight = height * 0.75f
        val minHeight = 4.dp.toPx()

        for (i in 0 until barCount) {
            val barCenterX = (i + 0.5f) * step
            val amp = amplitudes.getOrElse(i) { 0.3f }
            val barHeight = (minHeight + amp * (maxHeight - minHeight)).coerceIn(minHeight, maxHeight)
            val isPlayed = barCenterX <= progressX

            val color = if (isPlayed) playedColor else unplayedColor

            // Top symmetric bar
            drawLine(
                color = color,
                start = Offset(barCenterX, centerY),
                end = Offset(barCenterX, centerY - barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
            // Bottom mirrored bar (subtly shorter reflection)
            drawLine(
                color = color.copy(alpha = if (isPlayed) 0.65f else 0.40f),
                start = Offset(barCenterX, centerY),
                end = Offset(barCenterX, centerY + barHeight * 0.65f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // Sleek Vertical Laser Marker
        val laserHeight = height * 0.90f
        drawLine(
            color = playheadColor.copy(alpha = 0.35f),
            start = Offset(progressX, centerY - laserHeight / 2f),
            end = Offset(progressX, centerY + laserHeight / 2f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = playheadColor,
            start = Offset(progressX, centerY - laserHeight / 2f),
            end = Offset(progressX, centerY + laserHeight / 2f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = Offset(progressX, centerY)
        )
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
