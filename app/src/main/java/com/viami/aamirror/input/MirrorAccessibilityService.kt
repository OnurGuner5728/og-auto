package com.onurguner.ogauto.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class MirrorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("OgAutoTouch", "service connected by Android")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        Log.w("OgAutoTouch", "service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        Log.w("OgAutoTouch", "service destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: MirrorAccessibilityService? = null
            private set

        /** The system setting can remain enabled while the service is disconnected. */
        fun enabledBySystem(context: Context): Boolean {
            val component = ComponentName(context, MirrorAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }

        fun ready(context: Context): Boolean = enabledBySystem(context) && instance != null

        fun pressBack(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false

        fun pressHome(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false

        fun pressRecents(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false

        fun openNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /**
         * Two-finger pinch around ([cx], [cy]): fingers start [startGap]
         * apart horizontally and end [endGap] apart.
         */
        fun pinch(cx: Float, cy: Float, startGap: Float, endGap: Float, maxX: Float, maxY: Float): Boolean {
            val service = instance ?: return false
            fun clampX(v: Float) = v.coerceIn(1f, maxX - 1f)
            val y = cy.coerceIn(1f, maxY - 1f)
            val a = Path().apply {
                moveTo(clampX(cx - startGap / 2), y); lineTo(clampX(cx - endGap / 2), y)
            }
            val b = Path().apply {
                moveTo(clampX(cx + startGap / 2), y); lineTo(clampX(cx + endGap / 2), y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(a, 0, 220))
                .addStroke(GestureDescription.StrokeDescription(b, 0, 220))
                .build()
            return send(service, gesture)
        }

        /** Taps the phone screen at ([x], [y]) in current display coordinates. */
        fun tap(x: Float, y: Float): Boolean = dispatch(
            Path().apply { moveTo(x, y) },
            durationMs = 50,
        )

        /** Swipes the phone screen from ([startX], [startY]) to ([endX], [endY]). */
        fun swipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long,
        ): Boolean = dispatch(
            Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            },
            durationMs,
        )

        private fun send(service: MirrorAccessibilityService, gesture: GestureDescription): Boolean {
            if (!enabledBySystem(service)) return false
            return try {
                service.dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w("OgAutoTouch", "gesture cancelled")
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: RuntimeException) {
                Log.w("OgAutoTouch", "gesture dispatch failed", e)
                false
            }
        }

        private fun dispatch(path: Path, durationMs: Long): Boolean {
            val service = instance ?: return false
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            return send(service, gesture)
        }
    }
}
