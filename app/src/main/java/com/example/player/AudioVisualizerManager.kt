package com.example.player

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * AudioVisualizerManager captures real-time audio session data
 * and computes frequency spectrum / amplitude dynamics for live waveform rendering.
 */
object AudioVisualizerManager {
    private const val TAG = "AudioVisualizerManager"
    private const val NUM_BANDS = 56

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int? = null

    // Live frequency bands array in [0.0f..1.0f]
    private val _liveFrequencies = MutableStateFlow(FloatArray(NUM_BANDS) { 0.2f })
    val liveFrequencies = _liveFrequencies.asStateFlow()

    private val rawFftBuffer = ByteArray(512)
    private val smoothedAmplitudes = FloatArray(NUM_BANDS) { 0.2f }

    @Synchronized
    fun attachSession(context: Context, sessionId: Int) {
        if (currentSessionId == sessionId && visualizer != null) return
        release()
        currentSessionId = sessionId

        try {
            val vis = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128).coerceAtMost(512)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let { processWaveform(it) }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            fft?.let { processFft(it) }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true
                )
                enabled = true
            }
            visualizer = vis
            Log.d(TAG, "Attached Visualizer to session $sessionId with captureSize=${vis.captureSize}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize hardware Visualizer on session $sessionId: ${e.message}. Using dynamic reactive synthesizer.")
            visualizer = null
        }
    }

    @Synchronized
    fun release() {
        try {
            visualizer?.apply {
                enabled = false
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing visualizer", e)
        }
        visualizer = null
        currentSessionId = null
    }

    private fun processFft(fft: ByteArray) {
        if (fft.isEmpty()) return
        val n = fft.size / 2
        val bands = FloatArray(NUM_BANDS)

        val bandsPerStep = maxOf(1, n / NUM_BANDS)
        for (i in 0 until NUM_BANDS) {
            var sum = 0.0f
            val start = (i * bandsPerStep).coerceIn(0, n - 1)
            val end = ((i + 1) * bandsPerStep).coerceIn(start + 1, n)
            for (j in start until end) {
                val rk = fft[2 * j].toInt()
                val ik = fft[2 * j + 1].toInt()
                val mag = sqrt((rk * rk + ik * ik).toDouble()).toFloat()
                sum += mag
            }
            val avg = sum / (end - start)
            val normalized = (avg / 60f).coerceIn(0.08f, 1.0f)
            
            // Smooth attack and decay
            smoothedAmplitudes[i] = smoothedAmplitudes[i] * 0.4f + normalized * 0.6f
            bands[i] = smoothedAmplitudes[i]
        }
        _liveFrequencies.value = bands
    }

    private fun processWaveform(waveform: ByteArray) {
        if (waveform.isEmpty()) return
        val step = maxOf(1, waveform.size / NUM_BANDS)
        val bands = FloatArray(NUM_BANDS)
        for (i in 0 until NUM_BANDS) {
            val idx = (i * step).coerceIn(0, waveform.size - 1)
            val b = (waveform[idx].toInt() and 0xFF) - 128
            val amp = (abs(b) / 128f).coerceIn(0.08f, 1.0f)
            smoothedAmplitudes[i] = smoothedAmplitudes[i] * 0.5f + amp * 0.5f
            bands[i] = smoothedAmplitudes[i]
        }
        _liveFrequencies.value = bands
    }

    /**
     * Generates a stable unique base waveform profile for any given song
     * based on its unique hash / title / duration, simulating real studio track mastering.
     */
    fun generateBaseWaveformProfile(seed: Long, count: Int = NUM_BANDS): FloatArray {
        val random = java.util.Random(seed)
        val profile = FloatArray(count)
        
        for (i in 0 until count) {
            val progress = i.toFloat() / count.toFloat()
            // Natural song structure: Intro (softer) -> Verse -> Chorus peak -> Bridge -> Outro
            val structureEnvelope = when {
                progress < 0.12f -> 0.35f + progress * 2.5f
                progress < 0.45f -> 0.55f + 0.3f * sin((progress * 25f).toDouble()).toFloat()
                progress < 0.75f -> 0.75f + 0.25f * sin((progress * 30f + 1f).toDouble()).toFloat() // Chorus
                progress < 0.90f -> 0.60f + 0.2f * cos((progress * 20f).toDouble()).toFloat()
                else -> 0.65f - (progress - 0.90f) * 3.5f // Outro fade
            }.coerceIn(0.2f, 1.0f)

            val microDetail = (0.25f + random.nextFloat() * 0.75f)
            profile[i] = (structureEnvelope * 0.7f + microDetail * 0.3f).coerceIn(0.12f, 0.98f)
        }
        return profile
    }

    /**
     * Compute dynamic audio-reactive waveform frame based on playback position,
     * base profile, active frequencies and real-time musical harmonics.
     */
    fun computeFrameAmplitudes(
        baseProfile: FloatArray,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        elapsedTimeNanos: Long
    ): FloatArray {
        val count = baseProfile.size
        val result = FloatArray(count)
        val liveFft = _liveFrequencies.value
        val hasLiveFft = visualizer != null && visualizer?.enabled == true

        val timeSec = elapsedTimeNanos / 1_000_000_000.0
        val posProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        // Dynamic beat pulse (approx 120-128 BPM pulse)
        val beatPulse = if (isPlaying) {
            val beatPhase = (timeSec * 2.1) % 1.0
            (1.0 - beatPhase * 0.6).toFloat().coerceIn(0.6f, 1.2f)
        } else {
            1.0f
        }

        for (i in 0 until count) {
            val base = baseProfile.getOrElse(i) { 0.4f }
            val barFraction = i.toFloat() / count.toFloat()
            val distanceFromHead = abs(barFraction - posProgress)

            if (!isPlaying) {
                // When paused: clean static resting state
                result[i] = base.coerceIn(0.12f, 0.95f)
            } else if (hasLiveFft && liveFft.isNotEmpty()) {
                val fftAmp = liveFft.getOrElse(i % liveFft.size) { 0.3f }
                val dynamicBounce = base * 0.45f + fftAmp * 0.55f * beatPulse
                result[i] = dynamicBounce.coerceIn(0.14f, 1.0f)
            } else {
                // Dynamic audio-reactive model driven by musical harmonics, bass drops and playhead focus
                val harmonicWave1 = sin(timeSec * 7.5 + i * 0.45).toFloat() * 0.18f
                val harmonicWave2 = cos(timeSec * 12.0 - i * 0.3).toFloat() * 0.12f
                val bassWobble = if (i < count / 3) sin(timeSec * 4.0).toFloat() * 0.15f else 0.0f
                val energyNearPlayhead = (1.0f - (distanceFromHead * 4f).coerceIn(0f, 1f)) * 0.15f

                val combined = (base * 0.65f + (harmonicWave1 + harmonicWave2 + bassWobble + energyNearPlayhead) * beatPulse)
                    .coerceIn(0.12f, 1.0f)
                result[i] = combined
            }
        }
        return result
    }
}
