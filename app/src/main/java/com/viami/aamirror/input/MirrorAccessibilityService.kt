package com.onurguner.ogauto.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class MirrorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: MirrorAccessibilityService? = null
            private set

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
            return service.dispatchGesture(gesture, null, null)
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

        private fun dispatch(path: Path, durationMs: Long): Boolean {
            val service = instance ?: return false
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            return service.dispatchGesture(gesture, null, null)
        }
    }
}
