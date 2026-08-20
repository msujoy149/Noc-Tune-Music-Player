package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.sin

/**
 * 10-Style Dynamic Progress Bar Selection Dialog for Noc Tune:
 * Fully responsive and adaptive across all screen sizes, foldables, tablets, TVs, and phones with navigation / gesture bars.
 * Features 10 distinct, audio-reactive progress bar styles with live animated mini previews,
 * custom 10-color palette selection, smooth brightness control, and instant color reset.
 */
@Composable
fun ProgressBarSelectionDialog(
    currentStyle: ProgressBarStyle,
    colorConfig: ProgressBarColorConfig,
    onStyleSelected: (ProgressBarStyle) -> Unit,
    onColorConfigChanged: (ProgressBarColorConfig) -> Unit,
    onDismissRequest: () -> Unit
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val deepEspresso = appColors.deepEspresso
    val coffeeBrown = appColors.coffeeBrown
    val warmCream = appColors.warmCream
    val softLatte = appColors.softLatte
    val secondaryText = appColors.secondaryText

    val context = LocalContext.current
    var selectedStyleState by remember { mutableStateOf(currentStyle) }
    var selectedStyleColorConfig by remember(selectedStyleState) {
        mutableStateOf(ProgressBarPreferences.getColorConfigForStyle(context, selectedStyleState))
    }

    // High-performance background animation engine throttled to 60fps for mini previews
    val previewEngine = remember { AudioReactiveEngine(bufferSize = 48, targetFps = 60) }
    LaunchedEffect(Unit) {
        previewEngine.runLoop(isPlaying = true, isAnimated = true)
    }

    val previewTick = previewEngine.frameTick
    val previewTimeSec = previewEngine.timeSec
    val previewAmplitudes = previewEngine.getAmplitudes()

    val staticAmplitudes = remember {
        FloatArray(48) { i ->
            val p = i.toFloat() / 48f
            (0.35f + 0.30f * sin(p * Math.PI * 3.5).toFloat()).coerceIn(0.15f, 0.85f)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Safe insets container: prevents clipping under gesture bar, navigation bar, camera cutout, or status bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = darkMocha,
                border = BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.5f)),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Pinned Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(coffeeBrown.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinearScale,
                                    contentDescription = null,
                                    tint = warmCream,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Progress Bar Style",
                                    color = warmCream,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "11 dynamic styles & color customization",
                                    color = secondaryText,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(36.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(coffeeBrown.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close dialog",
                                tint = warmCream,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = coffeeBrown.copy(alpha = 0.25f), thickness = 1.dp)

                    // Scrollable area for the 11 progress bar styles
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
                    ) {
                        items(ProgressBarStyle.entries.toTypedArray()) { style ->
                            val isSelected = style == selectedStyleState
                            val styleConfig = if (isSelected) {
                                selectedStyleColorConfig
                            } else {
                                remember(style) { ProgressBarPreferences.getColorConfigForStyle(context, style) }
                            }
                            val styleEffectiveColor = styleConfig.getEffectiveColor(style)
                            val isAnimEnabled = styleConfig.isAnimationEnabled

                            StyleOptionItem(
                                style = style,
                                isSelected = isSelected,
                                effectiveColor = styleEffectiveColor,
                                isAnimationEnabled = isAnimEnabled,
                                amplitudes = if (isAnimEnabled) previewAmplitudes else staticAmplitudes,
                                timeSec = if (isAnimEnabled) previewTimeSec else 0f,
                                onClick = {
                                    selectedStyleState = style
                                    val newStyleConfig = ProgressBarPreferences.getColorConfigForStyle(context, style)
                                    selectedStyleColorConfig = newStyleConfig
                                    ProgressBarPreferences.setStyle(context, style)
                                    onStyleSelected(style)
                                    onColorConfigChanged(newStyleConfig)
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        color = coffeeBrown.copy(alpha = 0.25f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    // Pinned Color Palette & Brightness Customization Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(deepEspresso.copy(alpha = 0.8f))
                            .border(1.dp, coffeeBrown.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        // Header: Title + Animation Toggle + Reset Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Clean Title without redundant progress bar name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = softLatte,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Color & Brightness",
                                    color = warmCream,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Actions: Animation Toggle (for all styles except Original) + Reset Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (selectedStyleState != ProgressBarStyle.ORIGINAL) {
                                    val isAnimOn = selectedStyleColorConfig.isAnimationEnabled
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAnimOn) softLatte.copy(alpha = 0.22f) else coffeeBrown.copy(alpha = 0.15f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isAnimOn) softLatte.copy(alpha = 0.6f) else coffeeBrown.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val newConfig = selectedStyleColorConfig.copy(
                                                    isAnimationEnabled = !isAnimOn
                                                )
                                                selectedStyleColorConfig = newConfig
                                                ProgressBarPreferences.setColorConfigForStyle(context, selectedStyleState, newConfig)
                                                onColorConfigChanged(newConfig)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isAnimOn) Icons.Default.GraphicEq else Icons.Default.MotionPhotosOff,
                                                contentDescription = "Toggle Animation",
                                                tint = if (isAnimOn) warmCream else secondaryText,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (isAnimOn) "Anim ON" else "Anim OFF",
                                                color = if (isAnimOn) warmCream else secondaryText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Reset Button for selected style
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = coffeeBrown.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val defaultConfig = ProgressBarColorConfig(
                                                customColorHex = null,
                                                brightness = 1.0f,
                                                isAnimationEnabled = true
                                            )
                                            selectedStyleColorConfig = defaultConfig
                                            ProgressBarPreferences.resetColorConfigForStyle(context, selectedStyleState)
                                            onColorConfigChanged(defaultConfig)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = "Reset Color",
                                            tint = warmCream,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Reset",
                                            color = warmCream,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 10 Color Swatches
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(PROGRESS_BAR_PRESET_COLORS) { colorHex ->
                                val isColorSelected = selectedStyleColorConfig.customColorHex == colorHex
                                val baseColor = Color(colorHex.toInt())

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(baseColor)
                                        .border(
                                            width = if (isColorSelected) 2.5.dp else 1.dp,
                                            color = if (isColorSelected) Color.White else Color.Black.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            val newConfig = selectedStyleColorConfig.copy(customColorHex = colorHex)
                                            selectedStyleColorConfig = newConfig
                                            ProgressBarPreferences.setColorConfigForStyle(context, selectedStyleState, newConfig)
                                            onColorConfigChanged(newConfig)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isColorSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Color selected",
                                            tint = if (baseColor == Color.White || baseColor == Color(0xFFFFD600) || baseColor == Color(0xFFAEEA00)) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Brightness Slider Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brightness6,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Brightness",
                                color = secondaryText,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${(selectedStyleColorConfig.brightness * 100).toInt()}%",
                                color = softLatte,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val activeEffectiveColor = selectedStyleColorConfig.getEffectiveColor(selectedStyleState)

                        Slider(
                            value = selectedStyleColorConfig.brightness,
                            onValueChange = { newBrightness ->
                                val newConfig = selectedStyleColorConfig.copy(brightness = newBrightness)
                                selectedStyleColorConfig = newConfig
                                ProgressBarPreferences.setColorConfigForStyle(context, selectedStyleState, newConfig)
                                onColorConfigChanged(newConfig)
                            },
                            valueRange = 0.3f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = activeEffectiveColor,
                                activeTrackColor = activeEffectiveColor,
                                inactiveTrackColor = coffeeBrown.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleOptionItem(
    style: ProgressBarStyle,
    isSelected: Boolean,
    effectiveColor: Color,
    isAnimationEnabled: Boolean = true,
    amplitudes: FloatArray,
    timeSec: Float,
    onClick: () -> Unit
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val deepEspresso = appColors.deepEspresso
    val coffeeBrown = appColors.coffeeBrown
    val softLatte = appColors.softLatte
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    val borderColor = if (isSelected) effectiveColor else coffeeBrown.copy(alpha = 0.25f)
    val bgColor = if (isSelected) effectiveColor.copy(alpha = 0.16f) else deepEspresso.copy(alpha = 0.5f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Title & Radio Check
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = style.displayName,
                        color = if (isSelected) softLatte else warmCream,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (style == ProgressBarStyle.ORIGINAL) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = coffeeBrown.copy(alpha = 0.35f),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "Default",
                                color = softLatte,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Radio Check indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) effectiveColor else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) effectiveColor else secondaryText.copy(alpha = 0.5f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mini Live Preview Box (34dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0B1E).copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                when (style) {
                    ProgressBarStyle.ORIGINAL -> {
                        OriginalProgressBarCanvas(
                            fraction = 0.52f,
                            playedColor = effectiveColor,
                            unplayedColor = coffeeBrown.copy(alpha = 0.35f),
                            indicatorColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.DYNAMIC_WAVEFORM -> {
                        DynamicWaveformProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            playedBarColor = effectiveColor.copy(alpha = 0.85f),
                            unplayedBarColor = Color.White.copy(alpha = 0.75f),
                            centerLinePlayedColor = effectiveColor,
                            centerLineUnplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.DYNAMIC_PULSE_BARS -> {
                        DynamicPulseBarsProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.75f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.SMOOTH_WAVE_LINE -> {
                        SmoothWaveLineProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            timeSec = timeSec,
                            isPlaying = isAnimationEnabled,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.75f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.MINIMAL_AUDIO_BARS -> {
                        MinimalAudioBarsProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.NEON_SPECTRUM -> {
                        NeonSpectrumProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.MIRRORED_EQUALIZER -> {
                        MirroredEqualizerProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.ORBITAL_BEATS -> {
                        OrbitalBeatsProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            timeSec = timeSec,
                            isPlaying = isAnimationEnabled,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.CYBER_STEPS -> {
                        CyberStepsProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.GLOWING_RIBBON -> {
                        GlowingRibbonProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            timeSec = timeSec,
                            isPlaying = isAnimationEnabled,
                            playedColor = effectiveColor,
                            unplayedColor = Color.White.copy(alpha = 0.35f),
                            playheadColor = effectiveColor
                        )
                    }
                    ProgressBarStyle.SLANTED_WAVEFORM -> {
                        SlantedWaveformProgressBarCanvas(
                            fraction = 0.52f,
                            amplitudes = amplitudes,
                            playedBarColor = effectiveColor,
                            centerStrokeColor = effectiveColor,
                            unplayedBarColor = Color.White.copy(alpha = 0.85f),
                            thumbColor = effectiveColor
                        )
                    }
                }
            }
        }
    }
}
