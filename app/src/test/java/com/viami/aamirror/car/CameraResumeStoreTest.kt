package com.onurguner.ogauto.car
import org.junit.Assert.*
import org.junit.Test
class CameraResumeStoreTest {
 @Test fun ticketsExpireAfterTwoMinutes() {
   assertTrue(CameraResumeStore.isRecent(120000, 1000))
   assertFalse(CameraResumeStore.isRecent(300000, 1000))
   assertFalse(CameraResumeStore.isRecent(900, 1000))
   assertFalse(CameraResumeStore.isRecent(5000, 0))
 }
}
