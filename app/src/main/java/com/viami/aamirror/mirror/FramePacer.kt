package com.onurguner.ogauto.mirror

/**
 * Bounds renderer output without allowing undrawn images to accumulate in ImageReader.
 * Frame acquisition/closing is done on every callback; only rendering is paced.
 */
internal class FramePacer(maxFps: Int) {
    private val intervalMs = if (maxFps in 1..59) (1000L / maxFps).coerceAtLeast(1L) else 0L
    private var lastAttemptMs: Long? = null

    fun shouldDraw(nowMs: Long): Boolean {
        if (intervalMs == 0L) return true
        val previous = lastAttemptMs
        if (previous != null && nowMs >= previous && nowMs - previous < intervalMs) return false
        lastAttemptMs = nowMs
        return true
    }
}

/** Conservative recovery: only retry when new input arrives but the output has stalled. */
internal object MirrorRecoveryPolicy {
    const val STALL_MS = 8_000L
    const val RECENT_INPUT_MS = 3_000L
    const val COOLDOWN_MS = 25_000L
    const val MAX_AUTOMATIC_REBINDS = 2

    fun shouldRebind(
        nowMs: Long,
        modeStartedMs: Long,
        lastInputMs: Long,
        lastPostMs: Long,
        lastRecoveryMs: Long,
        recoveryCount: Int,
    ): Boolean {
        if (modeStartedMs <= 0L || lastInputMs <= 0L) return false
        if (recoveryCount >= MAX_AUTOMATIC_REBINDS) return false
        if (nowMs - lastInputMs !in 0L..RECENT_INPUT_MS) return false
        if (nowMs - maxOf(modeStartedMs, lastPostMs) < STALL_MS) return false
        if (lastRecoveryMs > 0L && nowMs - lastRecoveryMs < COOLDOWN_MS) return false
        return true
    }
}
