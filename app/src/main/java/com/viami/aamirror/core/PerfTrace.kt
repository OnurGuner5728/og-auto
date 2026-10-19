package com.onurguner.ogauto.core

import android.os.SystemClock
import android.util.Log

/** Low-overhead milestone timing; no overlays, raw URLs or user data. */
object PerfTrace {
    fun now(): Long = SystemClock.elapsedRealtime()
    fun elapsed(startMs: Long, endMs: Long): Long = (endMs - startMs).coerceAtLeast(0L)
    fun log(stage: String, started: Long) {
        Log.i("OgAutoPerf", "$"+"stage="+stage+" elapsedMs="+elapsed(started, now()))
    }
}
