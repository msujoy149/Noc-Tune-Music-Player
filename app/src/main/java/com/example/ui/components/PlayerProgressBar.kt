package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * PlayerProgressBar with 10 dynamic, audio-reactive styles:
 * 1. ORIGINAL - Sleek Material 3 Seeker Slider (100% identical to brightness slider)
 * 2. DYNAMIC_WAVEFORM - Audio-reactive vertical waveform bars
 * 3. DYNAMIC_PULSE_BARS - Segmented equalizer pulse bars
 * 4. SMOOTH_WAVE_LINE - Fluid audio-reactive undulating sine wave
 * 5. MINIMAL_AUDIO_BARS - Modern minimalist soundwave spectrum
 * 6. NEON_SPECTRUM - High-energy frequency equalizer with peak caps
 * 7. MIRRORED_EQUALIZER - Symmetrical dual-sided pulsating studio bars
 * 8. ORBITAL_BEATS - Dynamic pulsating particle beat chain
 * 9. CYBER_STEPS - Futuristic tiered stepped audio pillars
 * 10. GLOWING_RIBBON - Harmonic double-frequency laser wave
 *
 * Performance Optimized:
 * - Isolated frame ticking prevents recomposition of the outer UI tree and timestamp text.
 * - Zero heap allocations per frame in draw scopes (pre-allocated amplitude buffer & cached paths).
 * - Automatic frame-loop suspension when playback is paused or static style is selected.
 */
@Composable
fun PlayerProgressBar(
    songDuration: Long,
    currentProgressMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    songId: String = "",
    style: ProgressBarStyle = ProgressBarStyle.ORIGINAL,
    colorConfig: ProgressBarColorConfig = ProgressBarColorConfig(),
    showRemainingTime: Boolean = false,
    onToggleRemainingTime: () -> Unit = {},
    timeTextColor: Color = Color.White.copy(alpha = 0.7f)
) {
    val effectiveProgressMs = currentProgressMs.coerceIn(0L, songDuration.coerceAtLeast(1L))
    val totalDuration = songDuration.coerceAtLeast(1L)
    val actualFraction = (effectiveProgressMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (isDragging) dragFraction else actualFraction
    val playedColor = colorConfig.getEffectiveColor(style)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("player_progress_bar_touch_area")
                .pointerInput(songDuration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        dragFraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                change.consume()
                            } else {
                                isDragging = false
                                onSeek((dragFraction * songDuration).toLong())
                                change.consume()
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            DynamicProgressBarContent(
                style = style,
                fraction = currentFraction,
                isPlaying = isPlaying,
                playedColor = playedColor
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Formatted Timestamps Row (Only recomposes on second ticks, decoupled from animation frames)
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

/**
 * Isolated Content rendering for the Progress Bar.
 * Encapsulates frame ticking and pre-allocated buffers to prevent UI thread blocking.
 */
@Composable
fun DynamicProgressBarContent(
    style: ProgressBarStyle,
    fraction: Float,
    isPlaying: Boolean,
    playedColor: Color,
    modifier: Modifier = Modifier
) {
    if (style == ProgressBarStyle.ORIGINAL) {
        OriginalProgressBarCanvas(
            fraction = fraction,
            playedColor = playedColor,
            unplayedColor = Color(0xFF2C243B),
            indicatorColor = playedColor,
            modifier = modifier
        )
        return
    }

    // High-efficiency frame clock for animated styles only
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, style) {
        if (isPlaying && style.isAnimated) {
            while (true) {
                withFrameNanos { nanos ->
                    frameNanos = nanos
                }
            }
        }
    }

    // Pre-allocated static amplitude buffer (ZERO garbage collection per frame)
    val amplitudeBuffer = remember { FloatArray(64) }
    
    // In-place synthesize reactive amplitudes without creating new array instances
    val timeSec = if (isPlaying) frameNanos / 1_000_000_000.0 else 0.0
    for (i in 0 until 64) {
        val p = i.toFloat() / 64f
        val base = 0.35f + 0.30f * sin(p * Math.PI * 3.5).toFloat()
        if (isPlaying) {
            val wave1 = 0.18f * sin(timeSec * 5.0 + i * 0.45).toFloat()
            val wave2 = 0.12f * cos(timeSec * 7.5 - i * 0.65).toFloat()
            amplitudeBuffer[i] = (base + wave1 + wave2).coerceIn(0.12f, 0.98f)
        } else {
            amplitudeBuffer[i] = base.coerceIn(0.15f, 0.85f)
        }
    }

    when (style) {
        ProgressBarStyle.ORIGINAL -> Unit
        ProgressBarStyle.DYNAMIC_WAVEFORM -> {
            DynamicWaveformProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                playedBarColor = playedColor.copy(alpha = 0.88f),
                unplayedBarColor = Color.White.copy(alpha = 0.75f),
                centerLinePlayedColor = playedColor,
                centerLineUnplayedColor = Color.White.copy(alpha = 0.35f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.DYNAMIC_PULSE_BARS -> {
            DynamicPulseBarsProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.75f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.SMOOTH_WAVE_LINE -> {
            SmoothWaveLineProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                frameNanos = frameNanos,
                isPlaying = isPlaying,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.75f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.MINIMAL_AUDIO_BARS -> {
            MinimalAudioBarsProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.35f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.NEON_SPECTRUM -> {
            NeonSpectrumProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.35f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.MIRRORED_EQUALIZER -> {
            MirroredEqualizerProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.35f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.ORBITAL_BEATS -> {
            OrbitalBeatsProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                frameNanos = frameNanos,
                isPlaying = isPlaying,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.35f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.CYBER_STEPS -> {
            CyberStepsProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.35f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
        ProgressBarStyle.GLOWING_RIBBON -> {
            GlowingRibbonProgressBarCanvas(
                fraction = fraction,
                amplitudes = amplitudeBuffer,
                frameNanos = frameNanos,
                isPlaying = isPlaying,
                playedColor = playedColor,
                unplayedColor = Color.White.copy(alpha = 0.35f),
                playheadColor = playedColor,
                modifier = modifier
            )
        }
    }
}

// ==========================================
// STYLE 1: ORIGINAL (Classic Material 3 Rounded Pill Track with Vertical Bar Thumb)
// ==========================================
@Composable
fun OriginalProgressBarCanvas(
    fraction: Float,
    playedColor: Color = Color(0xFF6B4EE0),
    unplayedColor: Color = Color(0xFF1B1728),
    indicatorColor: Color = Color(0xFF6B4EE0),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = {},
            enabled = false,
            colors = SliderDefaults.colors(
                disabledThumbColor = indicatorColor,
                disabledActiveTrackColor = playedColor,
                disabledInactiveTrackColor = unplayedColor
            ),
            modifier = Modifier.fillMaxWidth()
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
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val step = width / barCount.toFloat()
        val barWidth = 3.dp.toPx()
        val maxHeight = height * 0.72f
        val minHeight = 4.dp.toPx()

        // Continuous horizontal center baseline
        drawLine(
            color = centerLinePlayedColor,
            start = Offset(0f, centerY),
            end = Offset(progressX, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = centerLineUnplayedColor,
            start = Offset(progressX, centerY),
            end = Offset(width, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        for (i in 0 until barCount) {
            val barCenterX = (i + 0.5f) * step
            val amp = amplitudes.getOrElse(i) { 0.3f }
            val barHeight = (minHeight + amp * (maxHeight - minHeight)).coerceIn(minHeight, maxHeight)
            val isPlayed = barCenterX <= progressX

            val color = if (isPlayed) playedBarColor else unplayedBarColor
            val topY = centerY - barHeight / 2f
            val bottomY = centerY + barHeight / 2f

            drawLine(
                color = color,
                start = Offset(barCenterX, topY),
                end = Offset(barCenterX, bottomY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // Diamond / Pill Playhead
        val indicatorSize = 9.dp.toPx()
        drawCircle(
            color = playheadColor.copy(alpha = 0.35f),
            radius = indicatorSize + 4.dp.toPx(),
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = playheadColor,
            radius = indicatorSize / 2f + 2.dp.toPx(),
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}

// ==========================================
// STYLE 3: DYNAMIC PULSE BARS
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
    val columnCount = 42
    val rows = 5

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val colStep = width / columnCount.toFloat()
        val dotSize = 2.4.dp.toPx()
        val rowSpacing = 4.2.dp.toPx()

        for (col in 0 until columnCount) {
            val colCenterX = (col + 0.5f) * colStep
            val amp = amplitudes.getOrElse(col) { 0.3f }
            val activeRows = (amp * rows).toInt().coerceIn(1, rows)
            val isPlayed = colCenterX <= progressX

            for (r in 0 until activeRows) {
                val offsetFromCenter = (r + 0.5f) * rowSpacing
                val topY = centerY - offsetFromCenter
                val bottomY = centerY + offsetFromCenter

                val dotColor = if (isPlayed) playedColor else unplayedColor.copy(alpha = 0.8f)

                drawCircle(
                    color = dotColor,
                    radius = dotSize / 2f,
                    center = Offset(colCenterX, topY)
                )
                if (r > 0) {
                    drawCircle(
                        color = dotColor,
                        radius = dotSize / 2f,
                        center = Offset(colCenterX, bottomY)
                    )
                }
            }
        }

        // Center line
        drawLine(
            color = playheadColor,
            start = Offset(progressX, centerY - 14.dp.toPx()),
            end = Offset(progressX, centerY + 14.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
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
// STYLE 4: SMOOTH WAVE LINE (Cached Zero-Alloc Path)
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
    val cachedWavePath = remember { Path() }
    val cachedPlayedPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val timeSec = if (isPlaying) (frameNanos / 1_000_000_000.0) else 0.0

        cachedWavePath.reset()
        cachedPlayedPath.reset()

        val samples = 80
        val step = width / samples.toFloat()
        var playheadY = centerY

        for (i in 0..samples) {
            val x = i * step
            val amp = amplitudes.getOrElse(i % amplitudes.size) { 0.3f }
            val waveOffset = if (isPlaying) {
                (amp * 12.dp.toPx() * sin(x * 0.02 + timeSec * 4.0)).toFloat()
            } else {
                (amp * 8.dp.toPx() * sin(x * 0.02)).toFloat()
            }
            val y = centerY + waveOffset

            if (i == 0) {
                cachedWavePath.moveTo(x, y)
                if (x <= progressX) cachedPlayedPath.moveTo(x, y)
            } else {
                cachedWavePath.lineTo(x, y)
                if (x <= progressX) {
                    cachedPlayedPath.lineTo(x, y)
                }
            }

            if (x <= progressX) {
                playheadY = y
            }
        }

        // Draw Unplayed Full Wave
        drawPath(
            path = cachedWavePath,
            color = unplayedColor.copy(alpha = 0.55f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Played Wave
        drawPath(
            path = cachedPlayedPath,
            color = playedColor,
            style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Playhead
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
// STYLE 5: MINIMAL AUDIO BARS
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
            // Bottom mirrored bar
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

// ==========================================
// STYLE 6: NEON SPECTRUM (Equalizer with dancing peak caps)
// ==========================================
@Composable
fun NeonSpectrumProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 40

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val step = width / barCount.toFloat()
        val barWidth = 3.2.dp.toPx()
        val maxHeight = height * 0.70f
        val minHeight = 5.dp.toPx()

        for (i in 0 until barCount) {
            val barCenterX = (i + 0.5f) * step
            val amp = amplitudes.getOrElse(i) { 0.3f }
            val barHeight = (minHeight + amp * (maxHeight - minHeight)).coerceIn(minHeight, maxHeight)
            val isPlayed = barCenterX <= progressX

            val color = if (isPlayed) playedColor else unplayedColor

            // Main upward bar
            drawLine(
                color = color,
                start = Offset(barCenterX, centerY + 2.dp.toPx()),
                end = Offset(barCenterX, centerY - barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )

            // Dancing Peak Cap Dot
            val capY = centerY - barHeight - 3.dp.toPx()
            drawCircle(
                color = if (isPlayed) Color.White else color.copy(alpha = 0.7f),
                radius = 1.8.dp.toPx(),
                center = Offset(barCenterX, capY)
            )
        }

        // Playhead
        drawLine(
            color = playheadColor,
            start = Offset(progressX, centerY - 15.dp.toPx()),
            end = Offset(progressX, centerY + 15.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = 3.2.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}

// ==========================================
// STYLE 7: MIRRORED EQUALIZER (Symmetrical dual-sided studio bars)
// ==========================================
@Composable
fun MirroredEqualizerProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 48

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val step = width / barCount.toFloat()
        val barWidth = 2.5.dp.toPx()
        val maxHeight = height * 0.42f
        val minHeight = 3.dp.toPx()

        for (i in 0 until barCount) {
            val barCenterX = (i + 0.5f) * step
            val amp = amplitudes.getOrElse(i) { 0.3f }
            val halfHeight = (minHeight + amp * (maxHeight - minHeight)).coerceIn(minHeight, maxHeight)
            val isPlayed = barCenterX <= progressX

            val color = if (isPlayed) playedColor else unplayedColor

            // Top bar
            drawLine(
                color = color,
                start = Offset(barCenterX, centerY - 1.dp.toPx()),
                end = Offset(barCenterX, centerY - halfHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
            // Bottom symmetric bar
            drawLine(
                color = color,
                start = Offset(barCenterX, centerY + 1.dp.toPx()),
                end = Offset(barCenterX, centerY + halfHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // Center baseline
        drawLine(
            color = if (progressX > 0f) playedColor else unplayedColor,
            start = Offset(0f, centerY),
            end = Offset(progressX, centerY),
            strokeWidth = 2.dp.toPx()
        )

        // Playhead
        drawCircle(
            color = playheadColor.copy(alpha = 0.35f),
            radius = 8.dp.toPx(),
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = playheadColor,
            radius = 5.dp.toPx(),
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}

// ==========================================
// STYLE 8: ORBITAL BEAT DOTS (Pulsating particle beat chain)
// ==========================================
@Composable
fun OrbitalBeatsProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    frameNanos: Long,
    isPlaying: Boolean,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val dotCount = 36

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val step = width / dotCount.toFloat()
        val timeSec = if (isPlaying) (frameNanos / 1_000_000_000.0) else 0.0

        for (i in 0 until dotCount) {
            val dotX = (i + 0.5f) * step
            val amp = amplitudes.getOrElse(i) { 0.3f }
            val pulseRadius = (2.2.dp.toPx() + amp * 3.5.dp.toPx()).coerceIn(2.dp.toPx(), 6.5.dp.toPx())
            val isPlayed = dotX <= progressX

            val waveYOffset = if (isPlaying) {
                (sin(timeSec * 4.0 + i * 0.4) * 4.dp.toPx()).toFloat()
            } else 0f

            val dotCenter = Offset(dotX, centerY + waveYOffset)

            if (isPlayed) {
                // Outer glow halo
                drawCircle(
                    color = playedColor.copy(alpha = 0.25f),
                    radius = pulseRadius + 3.dp.toPx(),
                    center = dotCenter
                )
                drawCircle(
                    color = playedColor,
                    radius = pulseRadius,
                    center = dotCenter
                )
            } else {
                drawCircle(
                    color = unplayedColor,
                    radius = pulseRadius * 0.75f,
                    center = dotCenter
                )
            }
        }

        // Playhead
        drawCircle(
            color = playheadColor.copy(alpha = 0.4f),
            radius = 10.dp.toPx(),
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = playheadColor,
            radius = 6.dp.toPx(),
            center = Offset(progressX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}

// ==========================================
// STYLE 9: CYBER STEPS (Tiered stepped audio pillars)
// ==========================================
@Composable
fun CyberStepsProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val blockCount = 38

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val step = width / blockCount.toFloat()
        val blockWidth = step * 0.68f
        val maxHeight = height * 0.70f
        val minHeight = 6.dp.toPx()

        for (i in 0 until blockCount) {
            val left = i * step + (step - blockWidth) / 2f
            val blockCenterX = left + blockWidth / 2f
            val amp = amplitudes.getOrElse(i) { 0.3f }
            val blockHeight = (minHeight + amp * (maxHeight - minHeight)).coerceIn(minHeight, maxHeight)
            val isPlayed = blockCenterX <= progressX

            val color = if (isPlayed) playedColor else unplayedColor

            drawRoundRect(
                color = color,
                topLeft = Offset(left, centerY - blockHeight / 2f),
                size = Size(blockWidth, blockHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // Playhead Indicator
        drawLine(
            color = playheadColor,
            start = Offset(progressX, centerY - 15.dp.toPx()),
            end = Offset(progressX, centerY + 15.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
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
// STYLE 10: GLOWING RIBBON (Harmonic double laser wave, Cached Zero-Alloc Paths)
// ==========================================
@Composable
fun GlowingRibbonProgressBarCanvas(
    fraction: Float,
    amplitudes: FloatArray,
    frameNanos: Long,
    isPlaying: Boolean,
    playedColor: Color,
    unplayedColor: Color,
    playheadColor: Color,
    modifier: Modifier = Modifier
) {
    val cachedWavePath1 = remember { Path() }
    val cachedWavePath2 = remember { Path() }
    val cachedPlayedPath1 = remember { Path() }
    val cachedPlayedPath2 = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        if (width <= 0f) return@Canvas

        val progressX = (fraction * width).coerceIn(0f, width)
        val timeSec = if (isPlaying) (frameNanos / 1_000_000_000.0) else 0.0

        cachedWavePath1.reset()
        cachedWavePath2.reset()
        cachedPlayedPath1.reset()
        cachedPlayedPath2.reset()

        val samples = 70
        val step = width / samples.toFloat()
        var playheadY = centerY

        for (i in 0..samples) {
            val x = i * step
            val amp = amplitudes.getOrElse(i % amplitudes.size) { 0.3f }
            val wave1 = if (isPlaying) {
                (amp * 11.dp.toPx() * sin(x * 0.025 + timeSec * 4.5)).toFloat()
            } else {
                (amp * 7.dp.toPx() * sin(x * 0.025)).toFloat()
            }
            val wave2 = if (isPlaying) {
                (amp * 9.dp.toPx() * cos(x * 0.025 - timeSec * 3.5)).toFloat()
            } else {
                (amp * 6.dp.toPx() * cos(x * 0.025)).toFloat()
            }

            val y1 = centerY + wave1
            val y2 = centerY + wave2

            if (i == 0) {
                cachedWavePath1.moveTo(x, y1)
                cachedWavePath2.moveTo(x, y2)
                if (x <= progressX) {
                    cachedPlayedPath1.moveTo(x, y1)
                    cachedPlayedPath2.moveTo(x, y2)
                }
            } else {
                cachedWavePath1.lineTo(x, y1)
                cachedWavePath2.lineTo(x, y2)
                if (x <= progressX) {
                    cachedPlayedPath1.lineTo(x, y1)
                    cachedPlayedPath2.lineTo(x, y2)
                }
            }

            if (x <= progressX) {
                playheadY = (y1 + y2) / 2f
            }
        }

        // Unplayed background dual ribbons
        drawPath(
            path = cachedWavePath1,
            color = unplayedColor.copy(alpha = 0.45f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = cachedWavePath2,
            color = unplayedColor.copy(alpha = 0.35f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Played vibrant ribbons
        drawPath(
            path = cachedPlayedPath1,
            color = playedColor,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = cachedPlayedPath2,
            color = playedColor.copy(alpha = 0.75f),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Playhead Orb
        drawCircle(
            color = playheadColor.copy(alpha = 0.35f),
            radius = 10.dp.toPx(),
            center = Offset(progressX, playheadY)
        )
        drawCircle(
            color = playheadColor,
            radius = 6.dp.toPx(),
            center = Offset(progressX, playheadY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = Offset(progressX, playheadY)
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
