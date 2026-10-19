package com.onurguner.ogauto.mirror

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import com.onurguner.ogauto.core.AspectFit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Copies the latest MediaProjection image to Android Auto's surface.
 *
 * Crucial: acquireLatestImage() and close() happen for EVERY ImageReader
 * notification, including throttled frames. Holding images in the reader
 * while waiting for the next 24/30 fps slot back-pressures VirtualDisplay
 * and can leave the whole AA surface stuck when another app starts video.
 */
@RequiresApi(29)
class FillRenderer(
    private val target: SurfaceTarget,
    phoneWidth: Int,
    phoneHeight: Int,
    private val fill: Boolean,
    maxFps: Int,
    maxCaptureEdge: Int,
) {
    private val thread = HandlerThread("mirror-renderer").apply { start() }
    private val handler = Handler(thread.looper)
    private val pacer = FramePacer(maxFps)
    private val budget = FrameBudget()
    @Volatile private var released = false
    private var lastErrorLogMs = 0L

    val captureWidth: Int
    val captureHeight: Int

    private val reader: ImageReader
    private val src: Rect
    private val dst: Rect

    init {
        val scale = if (maxCaptureEdge > 0) {
            minOf(1f, maxCaptureEdge.toFloat() / max(phoneWidth, phoneHeight))
        } else 1f
        captureWidth = ((phoneWidth * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        captureHeight = ((phoneHeight * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        reader = ImageReader.newInstance(
            captureWidth, captureHeight, PixelFormat.RGBA_8888, 3,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        if (fill) {
            val crop = AspectFit.sourceCrop(captureWidth, captureHeight, target.width, target.height)
            src = Rect(crop.x, crop.y, crop.x + crop.width, crop.y + crop.height)
            dst = Rect(0, 0, target.width, target.height)
        } else {
            src = Rect(0, 0, captureWidth, captureHeight)
            val fit = AspectFit.fit(captureWidth, captureHeight, target.width, target.height)
            dst = Rect(fit.x, fit.y, fit.x + fit.width, fit.y + fit.height)
        }
        reader.setOnImageAvailableListener({ onFrame() }, handler)
        Log.i(TAG, "capture " + captureWidth + "x" + captureHeight +
            " -> car " + target.width + "x" + target.height +
            " fill=" + fill + " maxFps=" + maxFps)
    }

    val surface: Surface
        get() = reader.surface

    private fun onFrame() {
        if (released) return
        // Drain the queue FIRST, even for a frame which will be skipped.
        val image = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            if (!released) reportFailure("image acquisition failed", e)
            null
        } ?: return

        try {
            if (released) return
            MirrorStats.onFrameProduced()
            if (!pacer.shouldDraw(SystemClock.uptimeMillis()) || !budget.canDraw(SystemClock.uptimeMillis())) {
                MirrorStats.onFrameSkipped()
                return
            }
            val buffer = image.hardwareBuffer
            if (buffer == null) {
                MirrorStats.onFrameDrawFailure()
                return
            }
            try {
                val bitmap = Bitmap.wrapHardwareBuffer(buffer, null)
                if (bitmap == null) {
                    MirrorStats.onFrameDrawFailure()
                    return
                }
                try {
                    // Count a POST, not a mere draw attempt.
                    val started = SystemClock.uptimeMillis()
                    if (drawFrame(bitmap)) {
                        budget.recordDrawTime(SystemClock.uptimeMillis() - started, SystemClock.uptimeMillis())
                        MirrorStats.onFrameDrawn()
                    } else {
                        MirrorStats.onFrameDrawFailure()
                    }
                } finally {
                    bitmap.recycle()
                }
            } finally {
                buffer.close()
            }
        } catch (e: Exception) {
            reportFailure("frame blit failed", e)
        } finally {
            image.close()
        }
    }

    private fun drawFrame(bitmap: Bitmap): Boolean {
        if (released || !target.surface.isValid) return false
        val canvas = try {
            target.surface.lockHardwareCanvas()
        } catch (_: IllegalArgumentException) {
            return false
        } catch (_: IllegalStateException) {
            return false
        }
        try {
            if (!fill) canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, src, dst, null)
        } finally {
            target.surface.unlockCanvasAndPost(canvas)
        }
        return true
    }

    private fun reportFailure(message: String, e: Exception) {
        MirrorStats.onFrameDrawFailure()
        val now = SystemClock.uptimeMillis()
        if (now - lastErrorLogMs >= 5_000L) {
            Log.w(TAG, message, e)
            lastErrorLogMs = now
        }
    }

    /**
     * Detach the producer before calling this. Wait briefly for the renderer
     * thread to finish using the old surface before rebinding a new renderer.
     */
    fun release() {
        if (released) return
        released = true
        reader.setOnImageAvailableListener(null, null)
        val done = CountDownLatch(1)
        val cleanup = Runnable {
            try {
                reader.close()
            } catch (e: Exception) {
                Log.w(TAG, "ImageReader close failed", e)
            } finally {
                thread.quitSafely()
                done.countDown()
            }
        }
        if (Looper.myLooper() == thread.looper) {
            cleanup.run()
        } else if (handler.post(cleanup)) {
            if (!done.await(500L, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "renderer cleanup timed out; driver may be blocked")
            }
        } else {
            Log.w(TAG, "renderer handler stopped before cleanup")
            cleanup.run()
        }
    }

    private companion object {
        const val TAG = "MirrorRenderer"
    }
}
