package com.onurguner.ogauto.mirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePacerTest {
    @Test fun caps30FpsWithoutDeferringImageOwnership() {
        val pacer = FramePacer(30)
        assertTrue(pacer.shouldDraw(1000))
        assertFalse(pacer.shouldDraw(1010))
        assertFalse(pacer.shouldDraw(1032))
        assertTrue(pacer.shouldDraw(1033))
        assertTrue(pacer.shouldDraw(1070))
    }

    @Test fun uncappedModeNeverRejects() {
        val pacer = FramePacer(60)
        assertTrue(pacer.shouldDraw(1000))
        assertTrue(pacer.shouldDraw(1000))
        assertTrue(pacer.shouldDraw(1001))
    }

    @Test fun clockResetDoesNotDeadlockRendering() {
        val pacer = FramePacer(24)
        assertTrue(pacer.shouldDraw(10000))
        assertTrue(pacer.shouldDraw(100))
    }

    @Test fun recoveryRequiresNewFramesAndOutputStall() {
        val now = 100_000L
        fun recover(input: Long, post: Long, previous: Long = 0, count: Int = 0) =
            MirrorRecoveryPolicy.shouldRebind(
                now, 80_000, input, post, previous, count
            )
        assertTrue(recover(99_500, 85_000))
        assertFalse(recover(0, 85_000))
        assertFalse(recover(80_000, 85_000))
        assertFalse(recover(99_500, 99_000))
        assertFalse(recover(99_500, 85_000, previous = 90_000))
        assertFalse(recover(99_500, 85_000, count = 2))
    }
}
