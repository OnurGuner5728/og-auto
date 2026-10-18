package com.onurguner.ogauto.car

import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import com.onurguner.ogauto.video.VideoPlayback

/** Surface owner for the native car video player. */
class VideoSink(private val carContext: CarContext) : SurfaceSink {
    override fun onAttach(container: SurfaceContainer) {
        val surface = container.surface ?: return
        VideoPlayback.attach(carContext, surface, container.width, container.height, container.dpi)
    }

    override fun onDetach() = VideoPlayback.detach()

    override fun onTap(x: Float, y: Float) = VideoPlayback.togglePause()
}
