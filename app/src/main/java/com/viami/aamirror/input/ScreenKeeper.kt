package com.onurguner.ogauto.input

import android.content.Context
import android.graphics.PixelFormat
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Keeps the phone screen on for as long as mirroring runs. MediaProjection
 * mirrors what the panel shows: once the screen times out the car gets the
 * lock screen, or a frozen last frame on some phones. Same technique as
 * Fermata's mirror: a keep-screen-on overlay window plus a dim wake lock
 * (the brightness itself is lowered separately by BrightnessSaver).
 *
 * The overlay is 1x1 and fully transparent: a full-screen one, even
 * invisible, would be an "untrusted touch" obscurer on Android 12+ and
 * swallow every tap on the phone.
 */
object ScreenKeeper {

    private const val TAG = "ScreenKeeper"
    private var view: View? = null
    private var wm: WindowManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun start(context: Context) {
        val app = context.applicationContext
        if (view == null && Settings.canDrawOverlays(app)) {
            try {
                val manager = app.getSystemService(WindowManager::class.java)
                val v = View(app)
                val lp = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    alpha = 0f
                }
                manager.addView(v, lp)
                view = v
                wm = manager
            } catch (e: RuntimeException) {
                Log.w(TAG, "keep-screen-on overlay failed", e)
            }
        }
        if (wakeLock == null) {
            val pm = app.getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "OgAuto:mirror",
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        }
    }

    fun stop() {
        view?.let { v -> runCatching { wm?.removeView(v) } }
        view = null
        wm = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
