package com.onurguner.ogauto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.onurguner.ogauto.R
import com.onurguner.ogauto.video.VideoPlayback

/**
 * Native player: the stream is decoded onto the car surface. Tap the
 * picture to pause; ⏮ ⏭ switch channel; the list icon opens the playlist.
 */
class VideoScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val sink = VideoSink(carContext)

    init {
        marker = ChannelListScreen.VIDEO_MARKER
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        VideoPlayback.init(carContext)
        SurfaceRouter.setSink(sink)
        VideoPlayback.resumeLastSelected(carContext)
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_menu) { screenManager.pop() })
            .addAction(action(R.drawable.ic_skip_prev) { VideoPlayback.previous() })
            .addAction(action(R.drawable.ic_play_pause) { VideoPlayback.togglePause() })
            .addAction(action(R.drawable.ic_skip_next) { VideoPlayback.next() })
            .build()
        val map = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_list) { screenManager.push(ChannelListScreen(carContext)) })
            .addAction(action(R.drawable.ic_reload) { VideoPlayback.retry() })
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setMapActionStrip(map)
            .build()
    }

    private fun action(iconRes: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setOnClickListener(onClick)
            .build()
}
