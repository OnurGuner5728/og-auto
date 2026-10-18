package com.onurguner.ogauto.input

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.View
import android.view.WindowManager

/**
 * Forces the whole phone into landscape through an invisible 0x0 overlay
 * window whose layout params carry a screenOrientation request (the
 * "Rotation Control" technique). Needs the SYSTEM_ALERT_WINDOW permission.
 */
object RotationLock {

    private var overlay: View? = null
    private var windowManager: WindowManager? = null

    private var locked = false

    val isLocked: Boolean
        get() = locked

    /** Either route works: the overlay request, or the system rotation setting. */
    fun canLock(context: Context): Boolean =
        Settings.canDrawOverlays(context) || Settings.System.canWrite(context)

    /** Flips the lock. Returns the new state (true = locked). */
    fun toggle(context: Context): Boolean {
        if (isLocked) unlock() else lock(context)
        return isLocked
    }

    fun lock(context: Context) {
        if (locked) return
        locked = true
        lockSystemRotation(context)
        if (!Settings.canDrawOverlays(context)) return
        val overlayContext = overlayContext(context.applicationContext)
        val manager = overlayContext.getSystemService(WindowManager::class.java)
        val view = View(overlayContext)
        val params = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        manager.addView(view, params)
        overlay = view
        windowManager = manager
    }

    fun unlock() {
        locked = false
        overlay?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlay = null
        windowManager = null
        restoreSystemRotation()
    }

    // Belt and braces, as Fermata does: with WRITE_SETTINGS also turn off
    // auto-rotate and set the user rotation to landscape, so launchers and
    // apps that ignore the overlay request still come up sideways.
    private var savedAccel: Int? = null
    private var savedUserRotation: Int? = null
    private var appContext: Context? = null

    private fun lockSystemRotation(context: Context) {
        if (!Settings.System.canWrite(context)) return
        val resolver = context.contentResolver
        try {
            if (savedAccel == null) {
                savedAccel = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
                savedUserRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, 0)
            }
            appContext = context.applicationContext
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, android.view.Surface.ROTATION_90)
        } catch (e: Exception) {
            android.util.Log.w("RotationLock", "system rotation lock failed", e)
        }
    }

    private fun restoreSystemRotation() {
        val context = appContext ?: return
        val accel = savedAccel ?: return
        try {
            val resolver = context.contentResolver
            savedUserRotation?.let { Settings.System.putInt(resolver, Settings.System.USER_ROTATION, it) }
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, accel)
        } catch (e: Exception) {
            android.util.Log.w("RotationLock", "system rotation restore failed", e)
        }
        savedAccel = null
        savedUserRotation = null
    }

    // createWindowContext requires a display-associated context: chain
    // through createDisplayContext or it throws UnsupportedOperationException.
    private fun overlayContext(appContext: Context): Context =
        if (Build.VERSION.SDK_INT >= 30) {
            val display = appContext.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
            appContext.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            appContext
        }
}
