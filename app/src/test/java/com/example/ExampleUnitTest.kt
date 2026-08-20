package com.example

import com.example.player.AudioVisualizerManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBaseWaveformProfileGeneration() {
    val profile = AudioVisualizerManager.generateBaseWaveformProfile(12345L, 48)
    assertEquals(48, profile.size)
    for (amp in profile) {
      assertTrue("Amplitude should be positive and <= 1.0", amp in 0.05f..1.0f)
    }
  }

  @Test
  fun testDynamicFrameAmplitudesWhenPaused() {
    val base = AudioVisualizerManager.generateBaseWaveformProfile(999L, 48)
    val frame = AudioVisualizerManager.computeFrameAmplitudes(
      baseProfile = base,
      positionMs = 15000L,
      durationMs = 210000L,
      isPlaying = false,
      elapsedTimeNanos = 0L
    )
    assertEquals(48, frame.size)
    for (i in 0 until 48) {
      assertEquals(base[i], frame[i], 0.001f)
    }
  }

  @Test
  fun testProgressBarStylesCountAndRegistration() {
    val styles = com.example.ui.components.ProgressBarStyle.entries
    assertEquals(11, styles.size)
    val slanted = com.example.ui.components.ProgressBarStyle.fromId("slanted_waveform")
    assertEquals(com.example.ui.components.ProgressBarStyle.SLANTED_WAVEFORM, slanted)
    assertTrue("Slanted waveform should be animated", slanted.isAnimated)
  }

  @Test
  fun testProgressBarColorConfigAnimationDefault() {
    val config = com.example.ui.components.ProgressBarColorConfig()
    assertTrue(config.isAnimationEnabled)
    val disabledConfig = config.copy(isAnimationEnabled = false)
    assertFalse(disabledConfig.isAnimationEnabled)
  }
}
