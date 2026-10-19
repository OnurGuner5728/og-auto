package com.onurguner.ogauto.mirror

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import com.onurguner.ogauto.core.MirrorSettings
import com.onurguner.ogauto.core.PerfTrace

/**
 * Mirrors the phone screen onto the car surface through a VirtualDisplay.
 * Android 14 forbids a second createVirtualDisplay on the same projection,
 * so the display is created once and re-targeted with resize() + setSurface().
 *
 * Fit mode targets the car surface directly (the system compositor
 * letterboxes). Fill mode targets a FillRenderer that center-crops every
 * frame onto the car surface; phone rotation re-builds that pipeline.
 */
class LocalScreenSource(
    private val context: Context,
    private val projection: MediaProjection,
) : FrameSource {

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var fillRenderer: FillRenderer? = null
    private var lastTarget: SurfaceTarget? = null
    private var lastPhoneSize: Pair<Int, Int>? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            if (fillRenderer == null) return
            val target = lastTarget ?: return
            // Rotation changes the phone size: rebuild the crop pipeline.
            if (PhoneDisplay.currentSize(context) != lastPhoneSize) attach(target)
        }
    }

    init {
        displayManager.registerDisplayListener(displayListener, handler)
    }

    override fun attach(target: SurfaceTarget) {
        val started = PerfTrace.now()
        if (!target.surface.isValid) {
            Log.w(TAG, "attach rejected: invalid car surface")
            return
        }
        lastTarget = target
        // The previous fill pipeline is released only after the
        // VirtualDisplay has moved off its ImageReader: releasing it first
        // leaves the display rendering into an abandoned surface for a few
        // frames, which some drivers never recover from (a frozen picture).
        val previous = fillRenderer
        fillRenderer = null
        // Fill drives the car surface through hwui, which the surface only
        // allows while nothing has claimed it for the CPU. When something
        // has, fall back to fit: a refused claim is a black screen at worst,
        // an honoured one would abort the process on the first frame.
        val paced = Build.VERSION.SDK_INT >= 29 && MirrorSettings.usesRenderer &&
            SurfaceMode.useHardware(target.surface, true)
        val fill = paced && MirrorSettings.fillScreen
        MirrorSettings.activeFill = fill
        if (paced) {
            // The old renderer still holds a hwui connection to the car
            // surface; drop it before the new one locks a hardware canvas.
            if (Build.VERSION.SDK_INT >= 29) {
                virtualDisplay?.surface = null
                previous?.release()
            }
            val (phoneWidth, phoneHeight) = PhoneDisplay.currentSize(context)
            lastPhoneSize = phoneWidth to phoneHeight
            val renderer = FillRenderer(
                target, phoneWidth, phoneHeight,
                fill = fill,
                maxFps = MirrorSettings.maxFps,
                maxCaptureEdge = MirrorSettings.maxCaptureEdge,
            )
            fillRenderer = renderer
            MirrorStats.setMode("paced ${renderer.captureWidth}x${renderer.captureHeight} " +
                "fill=$fill maxFps=${MirrorSettings.maxFps} car=${target.width}x${target.height}")
            Log.i(TAG, "paced: phone ${phoneWidth}x$phoneHeight fill=$fill onto " +
                "car ${target.width}x${target.height}")
            // The display renders at the reduced capture size; density
            // scaled with it keeps the layout identical to the phone's.
            val dpi = (target.densityDpi * renderer.captureWidth / phoneWidth.toFloat())
                .toInt().coerceAtLeast(120)
            retarget(renderer.captureWidth, renderer.captureHeight, dpi, renderer.surface)
        } else {
            Log.i(TAG, "fit: car ${target.width}x${target.height}")
            MirrorStats.setMode("direct car=${target.width}x${target.height} (no frame stats)")
            retarget(target.width, target.height, target.densityDpi, target.surface)
            if (Build.VERSION.SDK_INT >= 29) previous?.release()
        }
        PerfTrace.log("mirror_surface_attach", started)
    }

    /**
     * Re-binds the VirtualDisplay to the car surface from scratch. Used by
     * the car "reconnect video" button and after the captured content was
     * resized, the two situations in which a stalled picture recovers by
     * forcing a fresh producer connection.
     */
    fun refresh() {
        val target = lastTarget ?: return
        Log.i(TAG, "refresh: resetting stalled renderer connection")
        virtualDisplay?.surface = null
        attach(target)
    }

    /** Called when the captured content changes size (rotation, fold). */
    fun onContentResized() {
        val target = lastTarget ?: return
        if (fillRenderer != null && PhoneDisplay.currentSize(context) != lastPhoneSize) {
            attach(target)
        }
    }

    private fun retarget(width: Int, height: Int, densityDpi: Int, surface: Surface) {
        val existing = virtualDisplay
        if (existing == null) {
            virtualDisplay = projection.createVirtualDisplay(
                "aa-mirror",
                width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, handler,
            )
        } else {
            existing.resize(width, height, densityDpi)
            existing.setSurface(surface)
        }
    }

    override fun detach() {
        // Do not refresh an abandoned surface while the host reconnects.
        lastTarget = null
        virtualDisplay?.surface = null
        MirrorSettings.activeFill = false
        // fillRenderer is only ever created on SDK_INT >= 29 (see attach()).
        if (Build.VERSION.SDK_INT >= 29) fillRenderer?.release()
        fillRenderer = null
    }

    override fun release() {
        detach()
        virtualDisplay?.release()
        virtualDisplay = null
        displayManager.unregisterDisplayListener(displayListener)
    }

    private companion object {
        const val TAG = "LocalScreenSource"
    }
}
