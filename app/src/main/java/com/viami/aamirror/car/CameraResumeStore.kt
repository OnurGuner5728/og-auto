package com.onurguner.ogauto.car

import android.content.Context
import android.os.SystemClock

/**
 * When a rear-camera overlay interrupts the Android Auto host, restore the
 * most recently interrupted screen. A real disconnect might look identical,
 * so the ticket expires after two minutes and is consumed only once.
 * No capture permission is ever requested automatically.
 */
internal object CameraResumeStore {
    private const val PREFS = "camera_resume"
    private const val MODE = "mode"
    private const val WHEN = "when_elapsed"
    private const val WINDOW_MS = 120_000L

    fun mark(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MODE, mode)
            .putLong(WHEN, SystemClock.elapsedRealtime())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun isRecent(now: Long, saved: Long): Boolean =
        saved > 0L && now >= saved && now - saved <= WINDOW_MS

    fun consumeIfRecent(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getString(MODE, null)
        val saved = prefs.getLong(WHEN, 0L)
        clear(context)
        return mode?.takeIf { isRecent(SystemClock.elapsedRealtime(), saved) }
    }
}
