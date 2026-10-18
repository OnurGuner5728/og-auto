package com.onurguner.ogauto.mirror

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import com.onurguner.ogauto.core.AspectFit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Paced mirror pipeline. The projection renders the phone screen into an
 * ImageReader at a reduced resolution, and frames are blitted onto the car
 * surface with a hardware canvas — scaled to fit (letterbox) or fill (center
 * crop) — at most [maxFps] times a second.
 *
 * Why pace: Android Auto re-encodes the car surface to H.264 on the phone's
 * hardware encoder. A full-rate 1080p video mirrored 1:1 makes that encoder
 * work flat out while the same chip also decodes the stream; on many phones
 * the encoder stalls and the whole Android Auto display freezes. Fewer, smaller
 * frames keep it inside its budget.
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
    @Volatile
    private var released = false

    private val intervalMs: Long = if (maxFps in 1..59) 1000L / maxFps else 0L
    private var lastDrawMs = 0L
    private var drawScheduled = false

    /** Capture size: phone aspect, longest edge capped. */
    val captureWidth: Int
    val captureHeight: Int

    private val reader: ImageReader
    private val src: Rect
    private val dst: Rect

    init {
        val scale = if (maxCaptureEdge > 0) {
            minOf(1f, maxCaptureEdge.toFloat() / max(phoneWidth, phoneHeight))
        } else 1f
        // Even sizes keep every encoder and GPU path happy.
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
        Log.i(TAG, "capture ${captureWidth}x$captureHeight -> car ${target.width}x${target.height} " +
            "fill=$fill maxFps=$maxFps")
    }

    /** Where the VirtualDisplay must render. */
    val surface: Surface
        get() = reader.surface

    private val drawLatest = Runnable {
        drawScheduled = false
        drawNow()
    }

    private fun onFrame() {
        if (released) return
        MirrorStats.onFrameProduced()
        if (intervalMs == 0L) {
            drawNow()
            return
        }
        val wait = lastDrawMs + intervalMs - SystemClock.uptimeMillis()
        if (wait <= 0) {
            handler.removeCallbacks(drawLatest)
            drawScheduled = false
            drawNow()
        } else if (!drawScheduled) {
            // Leave the frames queued; the newest one is taken when due.
            drawScheduled = true
            handler.postDelayed(drawLatest, wait)
        }
    }

    private fun drawNow() {
        if (released) return
        val image = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            null
        } ?: return
        try {
            val buffer = image.hardwareBuffer ?: return
            buffer.use { hb ->
                val bitmap = Bitmap.wrapHardwareBuffer(hb, null) ?: return@use
                drawFrame(bitmap)
                bitmap.recycle()
                MirrorStats.onFrameDrawn()
            }
            lastDrawMs = SystemClock.uptimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "frame blit failed", e)
        } finally {
            image.close()
        }
    }

    private fun drawFrame(bitmap: Bitmap) {
        val canvas = try {
            target.surface.lockHardwareCanvas()
        } catch (e: IllegalArgumentException) {
            return
        } catch (e: IllegalStateException) {
            return
        }
        try {
            if (!fill) canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, src, dst, null)
        } finally {
            target.surface.unlockCanvasAndPost(canvas)
        }
    }

    fun release() {
        released = true
        reader.setOnImageAvailableListener(null, null)
        handler.removeCallbacks(drawLatest)
        handler.post {
            reader.close()
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "MirrorRenderer"
    }
}
