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
import androidx.compose.material.icons.filled.LinearScale
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
 * Responsive Progress Bar Selection Dialog for Noc Tune:
 * Fully adaptive across all screen sizes, foldables, tablets, TVs, and phones with navigation / gesture bars.
 * Allows user to choose between 5 real audio-reactive progress bar styles with live mini previews,
 * select custom progress bar colors from a 10-color palette, adjust brightness smoothly,
 * and reset colors back to default dynamically.
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
    var currentColorConfig by remember { mutableStateOf(colorConfig) }

    // Live frame ticker for animated mini previews
    var miniFrameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                miniFrameNanos = nanos
            }
        }
    }

    // Static sample base amplitudes for clean preview demonstration
    val previewAmplitudes = remember(miniFrameNanos) {
        val timeSec = miniFrameNanos / 1_000_000_000.0
        FloatArray(48) { i ->
            val p = i.toFloat() / 48f
            val base = 0.35f + 0.3f * sin(p * Math.PI * 3.0).toFloat()
            val bounce = 0.2f * sin(timeSec * 4.0 + i * 0.4).toFloat()
            (base + bounce).coerceIn(0.15f, 0.95f)
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                    text = "Choose visualizer & customize color",
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
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close dialog",
                                tint = secondaryText
                            )
                        }
                    }

                    HorizontalDivider(color = coffeeBrown.copy(alpha = 0.25f), thickness = 1.dp)

                    // Unified scrollable area containing all style choices and the color customization section
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 6.dp)
                    ) {
                        // 1. Five Style Options
                        items(ProgressBarStyle.entries.toTypedArray()) { style ->
                            val isSelected = style == currentStyle
                            val styleEffectiveColor = currentColorConfig.getEffectiveColor(style)
                            StyleOptionItem(
                                style = style,
                                isSelected = isSelected,
                                effectiveColor = styleEffectiveColor,
                                amplitudes = previewAmplitudes,
                                frameNanos = miniFrameNanos,
                                onClick = {
                                    ProgressBarPreferences.setStyle(context, style)
                                    onStyleSelected(style)
                                }
                            )
                        }

                        // 2. Color Palette & Brightness Customization Section
                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(deepEspresso.copy(alpha = 0.7f))
                                    .border(1.dp, coffeeBrown.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                // Header: Palette Title + Reset Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Reset Button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = coffeeBrown.copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val defaultConfig = ProgressBarColorConfig(customColorHex = null, brightness = 1.0f)
                                                currentColorConfig = defaultConfig
                                                ProgressBarPreferences.resetColorConfig(context)
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

                                Spacer(modifier = Modifier.height(10.dp))

                                // 10 Color Swatches (Smoothly scrollable horizontally)
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(PROGRESS_BAR_PRESET_COLORS) { colorHex ->
                                        val isColorSelected = currentColorConfig.customColorHex == colorHex
                                        val baseColor = Color(colorHex.toInt())

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(baseColor)
                                                .border(
                                                    width = if (isColorSelected) 2.5.dp else 1.dp,
                                                    color = if (isColorSelected) Color.White else Color.Black.copy(alpha = 0.35f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    val newConfig = currentColorConfig.copy(customColorHex = colorHex)
                                                    currentColorConfig = newConfig
                                                    ProgressBarPreferences.setColorConfig(context, newConfig)
                                                    onColorConfigChanged(newConfig)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isColorSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Color selected",
                                                    tint = if (baseColor == Color.White || baseColor == Color(0xFFFFD600) || baseColor == Color(0xFFAEEA00)) Color.Black else Color.White,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Brightness Slider Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Brightness6,
                                        contentDescription = null,
                                        tint = secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Brightness",
                                        color = secondaryText,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${(currentColorConfig.brightness * 100).toInt()}%",
                                        color = softLatte,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val activeEffectiveColor = currentColorConfig.getEffectiveColor(currentStyle)

                                Slider(
                                    value = currentColorConfig.brightness,
                                    onValueChange = { newBrightness ->
                                        val newConfig = currentColorConfig.copy(brightness = newBrightness)
                                        currentColorConfig = newConfig
                                        ProgressBarPreferences.setColorConfig(context, newConfig)
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
                                        .height(28.dp)
                                )
                            }
                        }
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
    amplitudes: FloatArray,
    frameNanos: Long,
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
                            unplayedColor = Color(0xFF1B1728),
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
                            frameNanos = frameNanos,
                            isPlaying = true,
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
                }
            }
        }
    }
}
