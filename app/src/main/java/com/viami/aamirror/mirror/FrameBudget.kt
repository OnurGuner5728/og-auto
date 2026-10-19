package com.onurguner.ogauto.mirror

/** Leave short breathing room after exceptionally slow GPU submits. */
internal class FrameBudget {
    private var resumeAtMs = 0L
    fun canDraw(nowMs: Long): Boolean = nowMs >= resumeAtMs
    fun recordDrawTime(drawMs: Long, nowMs: Long) {
        if (drawMs > 40L) resumeAtMs = nowMs + 20L
    }
}
