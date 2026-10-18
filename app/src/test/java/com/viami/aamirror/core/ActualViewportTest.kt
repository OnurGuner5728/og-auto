package com.onurguner.ogauto.core
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
class ActualViewportTest {
 @After fun reset() { MirrorSettings.fillScreen=false; MirrorSettings.activeFill=false }
 @Test fun requestedFillWithFitFallbackRejectsLetterboxTap() {
  MirrorSettings.fillScreen=true; MirrorSettings.activeFill=false
  assertNull(TouchMapper.mapCurrent(10f, 250f, 1000, 500, 500, 1000))
 }
 @Test fun actualFillAcceptsSameTapEvenAfterPreferenceChanges() {
  MirrorSettings.fillScreen=false; MirrorSettings.activeFill=true
  val point=TouchMapper.mapCurrent(10f, 250f, 1000, 500, 500, 1000)!!
  assertEquals(5f, point.x, 0.01f)
  assertEquals(500f, point.y, 0.01f)
 }
}
