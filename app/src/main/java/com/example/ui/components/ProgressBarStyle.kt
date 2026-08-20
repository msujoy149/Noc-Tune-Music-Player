package com.example.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class ProgressBarStyle(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    ORIGINAL(
        id = "original",
        displayName = "Original",
        subtitle = "Classic sleek seeker slider"
    ),
    DYNAMIC_WAVEFORM(
        id = "dynamic_waveform",
        displayName = "Dynamic Waveform",
        subtitle = "Audio-reactive vertical waveform bars"
    ),
    DYNAMIC_PULSE_BARS(
        id = "dynamic_pulse_bars",
        displayName = "Dynamic Pulse Bars",
        subtitle = "Segmented equalizer pulse bars"
    ),
    SMOOTH_WAVE_LINE(
        id = "smooth_wave_line",
        displayName = "Smooth Wave Line",
        subtitle = "Fluid audio-reactive undulating sine wave"
    ),
    MINIMAL_AUDIO_BARS(
        id = "minimal_audio_bars",
        displayName = "Minimal Audio Bars",
        subtitle = "Modern minimalist soundwave spectrum"
    ),
    NEON_SPECTRUM(
        id = "neon_spectrum",
        displayName = "Neon Spectrum",
        subtitle = "High-energy frequency equalizer with peak caps"
    ),
    MIRRORED_EQUALIZER(
        id = "mirrored_equalizer",
        displayName = "Mirrored Equalizer",
        subtitle = "Symmetrical dual-sided pulsating studio bars"
    ),
    ORBITAL_BEATS(
        id = "orbital_beats",
        displayName = "Orbital Beat Dots",
        subtitle = "Dynamic pulsating particle beat chain"
    ),
    CYBER_STEPS(
        id = "cyber_steps",
        displayName = "Cyber Steps",
        subtitle = "Futuristic tiered stepped audio pillars"
    ),
    GLOWING_RIBBON(
        id = "glowing_ribbon",
        displayName = "Glowing Ribbon",
        subtitle = "Harmonic double-frequency laser wave"
    ),
    SLANTED_WAVEFORM(
        id = "slanted_waveform",
        displayName = "Curved Acoustic Wave",
        subtitle = "Glowing curved ribs with horizontal center spine"
    );

    val isAnimated: Boolean get() = this != ORIGINAL

    companion object {
        val DEFAULT = ORIGINAL

        fun fromId(id: String?): ProgressBarStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * Progress Bar Color and Brightness Configuration per style.
 * - customColorHex: null or -1L means use the style's default color.
 *   Default for Style 1 (Original) is Theme Purple (0xFF6B4EE0L).
 *   Default for Styles 2-10 is Warm Amber (0xFFFF6A00L).
 * - brightness: 0.3f .. 1.5f (1.0f default).
 */
data class ProgressBarColorConfig(
    val customColorHex: Long? = null,
    val brightness: Float = 1.0f,
    val isAnimationEnabled: Boolean = true
) {
    fun getEffectiveColor(style: ProgressBarStyle): Color {
        val baseColor = if (customColorHex != null && customColorHex != -1L) {
            Color(customColorHex.toInt())
        } else {
            getDefaultColorForStyle(style)
        }
        return applyBrightness(baseColor, brightness)
    }

    companion object {
        fun getDefaultColorForStyle(style: ProgressBarStyle): Color {
            return when (style) {
                ProgressBarStyle.ORIGINAL -> Color(0xFF6B4EE0)
                else -> Color(0xFFFF6A00)
            }
        }

        fun applyBrightness(baseColor: Color, brightness: Float): Color {
            val b = brightness.coerceIn(0.2f, 1.6f)
            return Color(
                red = (baseColor.red * b).coerceIn(0f, 1f),
                green = (baseColor.green * b).coerceIn(0f, 1f),
                blue = (baseColor.blue * b).coerceIn(0f, 1f),
                alpha = baseColor.alpha
            )
        }
    }
}

val PROGRESS_BAR_PRESET_COLORS: List<Long> = listOf(
    0xFF6B4EE0L, // Theme Deep Purple / Violet
    0xFFFF6A00L, // Warm Amber / Orange
    0xFFFFD600L, // Vibrant Golden Yellow
    0xFF00E676L, // Neon Emerald Green
    0xFF00E5FFL, // Electric Cyan
    0xFF2979FFL, // Cobalt / Sky Blue
    0xFFFF2D55L, // Neon Rose / Pink
    0xFFFF3D00L, // Crimson Flame Red
    0xFFAEEA00L, // Lime Chartreuse
    0xFFFFFFFFL  // Pure White
)

object ProgressBarPreferences {
    private const val PREFS_NAME = "noc_tune_prefs"
    private const val KEY_PROGRESS_BAR_STYLE = "progress_bar_style_pref"
    private const val PREFIX_COLOR = "progress_bar_color_"
    private const val PREFIX_BRIGHTNESS = "progress_bar_brightness_"
    private const val PREFIX_ANIMATION = "progress_bar_animation_"

    fun getStyle(context: Context): ProgressBarStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_PROGRESS_BAR_STYLE, ProgressBarStyle.DEFAULT.id)
        return ProgressBarStyle.fromId(id)
    }

    fun setStyle(context: Context, style: ProgressBarStyle) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROGRESS_BAR_STYLE, style.id).apply()
    }

    /**
     * Get the color, brightness, and animation configuration specific to a given ProgressBarStyle.
     */
    fun getColorConfigForStyle(context: Context, style: ProgressBarStyle): ProgressBarColorConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getLong(PREFIX_COLOR + style.id, -1L)
        val brightness = prefs.getFloat(PREFIX_BRIGHTNESS + style.id, 1.0f)
        val animationEnabled = prefs.getBoolean(PREFIX_ANIMATION + style.id, true)
        return ProgressBarColorConfig(
            customColorHex = if (colorHex == -1L) null else colorHex,
            brightness = brightness,
            isAnimationEnabled = animationEnabled
        )
    }

    /**
     * Save color, brightness, and animation configuration for a specific ProgressBarStyle.
     */
    fun setColorConfigForStyle(context: Context, style: ProgressBarStyle, config: ProgressBarColorConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(PREFIX_COLOR + style.id, config.customColorHex ?: -1L)
            .putFloat(PREFIX_BRIGHTNESS + style.id, config.brightness)
            .putBoolean(PREFIX_ANIMATION + style.id, config.isAnimationEnabled)
            .apply()
    }

    /**
     * Reset configuration for a specific ProgressBarStyle.
     */
    fun resetColorConfigForStyle(context: Context, style: ProgressBarStyle) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(PREFIX_COLOR + style.id)
            .remove(PREFIX_BRIGHTNESS + style.id)
            .remove(PREFIX_ANIMATION + style.id)
            .apply()
    }

    // Backward-compatibility helpers that map to the currently selected style
    fun getColorConfig(context: Context): ProgressBarColorConfig {
        val currentStyle = getStyle(context)
        return getColorConfigForStyle(context, currentStyle)
    }

    fun setColorConfig(context: Context, config: ProgressBarColorConfig) {
        val currentStyle = getStyle(context)
        setColorConfigForStyle(context, currentStyle, config)
    }

    fun resetColorConfig(context: Context) {
        val currentStyle = getStyle(context)
        resetColorConfigForStyle(context, currentStyle)
    }
}
