package com.onurguner.ogauto.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import android.util.Log
import com.onurguner.ogauto.core.PerfTrace

/** A mode that can own the car surface (screen mirroring, browser, ...). */
interface SurfaceSink {
    fun onAttach(container: SurfaceContainer)
    fun onDetach()
    fun onTap(x: Float, y: Float) {}
    fun onScroll(distanceX: Float, distanceY: Float) {}
    fun onFling(velocityX: Float, velocityY: Float) {}
    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {}
}

/**
 * The single SurfaceCallback registered with the host. The host does not
 * re-deliver onSurfaceAvailable when the callback changes, so the router is
 * registered once per session and the active mode is swapped underneath it.
 */
object SurfaceRouter {

    var container: SurfaceContainer? = null
        private set

    private var sink: SurfaceSink? = null

    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            if (surfaceContainer.surface?.isValid != true) {
                Log.w("OgAutoSurface", "host supplied invalid car surface")
                return
            }
            val previous = container
            if (previous != null && previous.surface === surfaceContainer.surface &&
                previous.width == surfaceContainer.width &&
                previous.height == surfaceContainer.height &&
                previous.dpi == surfaceContainer.dpi) return
            if (previous != null) sink?.onDetach()
            container = surfaceContainer
            Log.i("OgAutoSurface", "attached car surface " + surfaceContainer.width + "x" + surfaceContainer.height)
            sink?.onAttach(surfaceContainer)
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            if (container?.surface !== surfaceContainer.surface) {
                Log.d("OgAutoSurface", "ignoring obsolete surface destroy")
                return
            }
            sink?.onDetach()
            container = null
            Log.i("OgAutoSurface", "car surface detached")
        }

        override fun onClick(x: Float, y: Float) {
            sink?.onTap(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            sink?.onScroll(distanceX, distanceY)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            sink?.onFling(velocityX, velocityY)
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            sink?.onScale(focusX, focusY, scaleFactor)
        }
    }

    fun register(carContext: CarContext) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
    }

    fun isActive(candidate: SurfaceSink): Boolean = sink === candidate

    /** Swaps the surface owner; no-op when the sink is already active. */
    fun setSink(newSink: SurfaceSink?) {
        if (newSink === sink) return
        val started = PerfTrace.now()
        sink?.onDetach()
        sink = newSink
        val current = container ?: return
        newSink?.onAttach(current)
        PerfTrace.log("surface_sink_switch", started)
    }
}
