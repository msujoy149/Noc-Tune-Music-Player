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
    );

    companion object {
        val DEFAULT = ORIGINAL

        fun fromId(id: String?): ProgressBarStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * Progress Bar Color and Brightness Configuration.
 * - customColorHex: null or -1L means use the style's default color.
 *   Default for Style 1 (Original) is Theme Purple (0xFF6B4EE0L).
 *   Default for Styles 2-5 (Waveforms/Pulse/Smooth/Minimal) is Warm Amber (0xFFFF6A00L).
 * - brightness: 0.3f .. 1.5f (1.0f default).
 */
data class ProgressBarColorConfig(
    val customColorHex: Long? = null,
    val brightness: Float = 1.0f
) {
    fun getEffectiveColor(style: ProgressBarStyle): Color {
        val baseColor = if (customColorHex != null && customColorHex != -1L) {
            Color(customColorHex.toInt())
        } else {
            when (style) {
                ProgressBarStyle.ORIGINAL -> Color(0xFF6B4EE0)
                else -> Color(0xFFFF6A00)
            }
        }
        return applyBrightness(baseColor, brightness)
    }

    companion object {
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
    private const val KEY_PROGRESS_BAR_COLOR = "progress_bar_color_pref"
    private const val KEY_PROGRESS_BAR_BRIGHTNESS = "progress_bar_brightness_pref"

    fun getStyle(context: Context): ProgressBarStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_PROGRESS_BAR_STYLE, ProgressBarStyle.DEFAULT.id)
        return ProgressBarStyle.fromId(id)
    }

    fun setStyle(context: Context, style: ProgressBarStyle) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROGRESS_BAR_STYLE, style.id).apply()
    }

    fun getColorConfig(context: Context): ProgressBarColorConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getLong(KEY_PROGRESS_BAR_COLOR, -1L)
        val brightness = prefs.getFloat(KEY_PROGRESS_BAR_BRIGHTNESS, 1.0f)
        return ProgressBarColorConfig(
            customColorHex = if (colorHex == -1L) null else colorHex,
            brightness = brightness
        )
    }

    fun setColorConfig(context: Context, config: ProgressBarColorConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_PROGRESS_BAR_COLOR, config.customColorHex ?: -1L)
            .putFloat(KEY_PROGRESS_BAR_BRIGHTNESS, config.brightness)
            .apply()
    }

    fun resetColorConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PROGRESS_BAR_COLOR)
            .remove(KEY_PROGRESS_BAR_BRIGHTNESS)
            .apply()
    }
}

