package com.onurguner.ogauto.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.onurguner.ogauto.browser.BrowserDisplay
import com.onurguner.ogauto.core.UrlResolver
import com.onurguner.ogauto.share.ShareInbox
import com.onurguner.ogauto.share.ShareServer
import com.onurguner.ogauto.video.VideoPlayback
import kotlinx.coroutines.launch

class MirrorSession : Session(), DefaultLifecycleObserver {

    private val shareServer = ShareServer(onUrl = ShareInbox::publish)
    private var restoreMode: String? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        owner.lifecycle.coroutineScope.launch {
            ShareInbox.links.collect { url -> openInBrowser(url) }
        }
        VideoPlayback.init(carContext)
        // A phone app handed a stream to OgAuto: bring the car player up.
        owner.lifecycle.coroutineScope.launch {
            VideoPlayback.openRequests.collect {
                val manager = carContext.getCarService(ScreenManager::class.java)
                if (manager.top !is VideoScreen) {
                    manager.popToRoot()
                    manager.push(VideoScreen(carContext))
                }
            }
        }
        // LAN sharing disabled in OgAuto.
    }

    override fun onStart(owner: LifecycleOwner) {
        val mode = restoreMode ?: return
        restoreMode = null
        val manager = carContext.getCarService(ScreenManager::class.java)
        if (manager.top !is HomeScreen) return
        val screen: Screen = when (mode) {
            "video" -> VideoScreen(carContext)
            "browser" -> BrowserScreen(carContext)
            "youtube" -> YouTubeScreen(carContext)
            "mirror" -> MirrorScreen(carContext)
            "split" -> V3SplitScreen(carContext)
            else -> return
        }
        manager.push(screen)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        shareServer.stop()
        VideoPlayback.stop()
    }

    private fun openInBrowser(url: String) {
        BrowserDisplay.loadUrl(UrlResolver.resolve(url))
        val manager = carContext.getCarService(ScreenManager::class.java)
        manager.popToRoot()
        manager.push(BrowserScreen(carContext))
    }

    override fun onCreateScreen(intent: Intent): Screen {
        restoreMode = CameraResumeStore.consumeIfRecent(carContext)
        return HomeScreen(carContext)
    }
}
