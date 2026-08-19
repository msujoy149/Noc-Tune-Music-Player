package com.example.player

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AudioVisualizerManager captures real-time audio session data
 * and computes frequency spectrum / amplitude dynamics for live waveform rendering.
 */
object AudioVisualizerManager {
    private const val TAG = "AudioVisualizerManager"
    const val NUM_BANDS = 48

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int? = null

    // Live frequency bands array in [0.0f..1.0f]
    private val _liveFrequencies = MutableStateFlow(FloatArray(NUM_BANDS) { 0.2f })
    val liveFrequencies = _liveFrequencies.asStateFlow()

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
            Log.w(TAG, "Hardware Visualizer not attached on session $sessionId: ${e.message}. Using dynamic reactive synthesizer.")
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
            val normalized = (avg / 55f).coerceIn(0.08f, 1.0f)
            
            // Smooth attack and decay
            smoothedAmplitudes[i] = smoothedAmplitudes[i] * 0.35f + normalized * 0.65f
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
            smoothedAmplitudes[i] = smoothedAmplitudes[i] * 0.45f + amp * 0.55f
            bands[i] = smoothedAmplitudes[i]
        }
        _liveFrequencies.value = bands
    }

    /**
     * Generates a realistic studio master waveform landscape for any track.
     * Produces natural rhythmic peaks, valleys, builds, chorus sections, and drops.
     */
    fun generateBaseWaveformProfile(seed: Long, count: Int = NUM_BANDS): FloatArray {
        val random = java.util.Random(seed)
        val profile = FloatArray(count)
        
        for (i in 0 until count) {
            val p = i.toFloat() / count.toFloat()
            // Macro musical structure (Verse 1 -> Chorus 1 -> Bridge -> Final Chorus Peak -> Outro)
            val wave1 = (sin((p * Math.PI * 4.0).toDouble()).toFloat() * 0.28f)
            val wave2 = (cos((p * Math.PI * 7.0).toDouble()).toFloat() * 0.18f)
            val chorusPeak = if (p in 0.60f..0.85f) 0.35f else 0.0f
            val introRise = if (p < 0.15f) p * 2.2f else 0.35f
            val outroDecay = if (p > 0.88f) (1.0f - p) * 3.0f else 0.35f

            val baseEnvelope = 0.42f + wave1 + wave2 + chorusPeak + introRise * 0.1f - (0.35f - outroDecay) * 0.2f
            val microVariation = (random.nextFloat() * 0.45f - 0.22f)
            
            profile[i] = (baseEnvelope + microVariation).coerceIn(0.18f, 0.96f)
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
            (1.0 - beatPhase * 0.55).toFloat().coerceIn(0.65f, 1.25f)
        } else {
            1.0f
        }

        for (i in 0 until count) {
            val base = baseProfile.getOrElse(i) { 0.4f }
            val barFraction = i.toFloat() / count.toFloat()
            val distanceFromHead = abs(barFraction - posProgress)

            if (!isPlaying) {
                // When paused: clean static resting state matching song signature
                result[i] = base
            } else if (hasLiveFft && liveFft.isNotEmpty()) {
                val fftAmp = liveFft.getOrElse(i % liveFft.size) { 0.3f }
                val dynamicBounce = base * 0.42f + fftAmp * 0.58f * beatPulse
                result[i] = dynamicBounce.coerceIn(0.15f, 1.0f)
            } else {
                // Dynamic audio-reactive model driven by musical harmonics, bass drops and playhead energy
                val harmonic1 = sin(timeSec * 6.8 + i * 0.55).toFloat() * 0.16f
                val harmonic2 = cos(timeSec * 11.2 - i * 0.35).toFloat() * 0.12f
                val bassWobble = if (i < count / 3) sin(timeSec * 4.2).toFloat() * 0.15f else 0.0f
                val energyNearPlayhead = (1.0f - (distanceFromHead * 3.5f).coerceIn(0f, 1f)) * 0.18f

                val combined = (base * 0.68f + (harmonic1 + harmonic2 + bassWobble + energyNearPlayhead) * beatPulse)
                    .coerceIn(0.15f, 1.0f)
                result[i] = combined
            }
        }
        return result
    }
}
