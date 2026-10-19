package com.onurguner.ogauto.car

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.onurguner.ogauto.core.Channel
import com.onurguner.ogauto.video.VideoPlayback

/**
 * Split layout inside OgAuto's OWN Android Auto surface; this is NOT a hook
 * into Google Maps/Android Auto Coolwalk panels.
 *
 * Left: independent web browser. Right: YouTube TV or the OgAuto native
 * IPTV channel. Third-party installed apps cannot be hosted in these views
 * without MediaProjection, privileged display permissions or their own API.
 */
internal object V3SplitDisplay {
    private const val TAG = "OgAutoV3Split"
    private val main = Handler(Looper.getMainLooper())
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: LinearLayout? = null
    private data class Pane(val frame: FrameLayout, val web: WebView?, val video: SurfaceView?)
    private val panes = mutableListOf<Pane>()
    private var player: ExoPlayer? = null
    private var horizontal = true
    private var selected = 0
    private var screenWidth = 1
    private var screenHeight = 1
    private var browserUrl = "https://www.google.com"
    private var youtubeUrl = "https://www.youtube.com/tv"
    private var showVideo = false
    private var videoIndex = -1

    fun attach(context: Context, surface: Surface, width: Int, height: Int, dpi: Int) = onMain {
        detachNow()
        if (!surface.isValid || width <= 0 || height <= 0) return@onMain
        screenWidth = width
        screenHeight = height
        try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay(
                "OgAuto-V3-Split", width, height, dpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
            display = vd
            val show = Presentation(context, vd.display)
            presentation = show
            root = LinearLayout(show.context).apply { setBackgroundColor(Color.BLACK) }
            val first = makeWeb(show.context, browserUrl, 0)
            val second = if (showVideo && VideoPlayback.current != null) {
                makeVideo(show.context)
            } else {
                showVideo = false
                makeWeb(show.context, youtubeUrl, 1)
            }
            panes.add(first)
            panes.add(second)
            rebuild()
            show.setContentView(root)
            show.show()
        } catch (e: Exception) {
            Log.e(TAG, "cannot build split screen", e)
            detachNow()
        }
    }

    private fun makeWeb(context: Context, url: String, index: Int): Pane {
        val frame = FrameLayout(context)
        val web = WebView(context)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, loadedUrl: String) {
                if (index == 0) browserUrl = loadedUrl else youtubeUrl = loadedUrl
            }
        }
        frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        web.loadUrl(url)
        return Pane(frame, web, null)
    }

    private fun makeVideo(context: Context): Pane {
        val frame = FrameLayout(context)
        val view = SurfaceView(context)
        frame.addView(view, FrameLayout.LayoutParams(-1, -1))
        frame.setBackgroundColor(Color.BLACK)
        return Pane(frame, null, view)
    }

    private fun rebuild() {
        val layout = root ?: return
        if (panes.size != 2) return
        layout.removeAllViews()
        layout.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        layout.addView(panes[0].frame, paneParams())
        val divider = android.view.View(layout.context).apply { setBackgroundColor(0xFF555555.toInt()) }
        val d = (3 * layout.resources.displayMetrics.density).toInt().coerceAtLeast(2)
        layout.addView(divider, if (horizontal) LinearLayout.LayoutParams(d, -1)
            else LinearLayout.LayoutParams(-1, d))
        layout.addView(panes[1].frame, paneParams())
        if (panes.any { it.video != null } && player == null) startVideo()
    }

    private fun paneParams(): LinearLayout.LayoutParams =
        if (horizontal) LinearLayout.LayoutParams(0, -1, 1f)
        else LinearLayout.LayoutParams(-1, 0, 1f)

    fun toggleOrientation() = onMain {
        horizontal = !horizontal
        rebuild()
    }

    fun swapPanels() = onMain {
        if (panes.size != 2) return@onMain
        val layout = root ?: return@onMain
        layout.removeAllViews()
        val second = panes.removeAt(1)
        panes.add(0, second)
        selected = 1 - selected
        rebuild()
    }

    /** Switch second content between YouTube TV and native IPTV. */
    fun toggleSecond(context: Context) = onMain {
        if (panes.size != 2) return@onMain
        if (!showVideo && VideoPlayback.current == null) {
            Toast.makeText(context, "Önce OgAuto Video bölümünden bir kanal seç", Toast.LENGTH_LONG).show()
            return@onMain
        }
        showVideo = !showVideo
        val layout = root ?: return@onMain
        val targetIndex = if (!showVideo) panes.indexOfFirst { it.video != null }.coerceAtLeast(0) else 1
        val previous = panes.removeAt(targetIndex)
        layout.removeAllViews()
        previous.web?.let { web ->
            previous.frame.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        stopVideo()
        val replacement = if (showVideo) {
            makeVideo(layout.context)
        } else makeWeb(layout.context, youtubeUrl, 1)
        panes.add(targetIndex, replacement)
        rebuild()
    }

    private fun startVideo() {
        val target = panes.firstOrNull { it.video != null }?.video ?: return
        val channel = channelToPlay() ?: return
        stopVideo()
        try {
            // The normal player is released before starting this second-panel
            // player so one stream is never decoded twice.
            VideoPlayback.stop()
            val http = DefaultHttpDataSource.Factory().apply {
                setUserAgent(channel.headers["User-Agent"] ?: "OgAutoV3")
                setDefaultRequestProperties(channel.headers.filterKeys { it != "User-Agent" })
            }
            val mediaSource = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(target.context, http)
            )
            val item = MediaItem.Builder().setUri(Uri.parse(channel.url)).apply {
                val lower = channel.url.substringBefore('?').lowercase()
                if (lower.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8)
                else if (lower.endsWith(".mpd")) setMimeType(MimeTypes.APPLICATION_MPD)
            }.build()
            val p = ExoPlayer.Builder(target.context).build()
            player = p
            p.setVideoSurfaceView(target)
            p.setMediaSource(mediaSource.createMediaSource(item))
            p.prepare()
            p.play()
        } catch (e: Exception) {
            Log.w(TAG, "split IPTV playback failed", e)
            stopVideo()
        }
    }

    private fun channelToPlay(): Channel? {
        val channels = VideoPlayback.channels.value
        if (channels.isEmpty()) return VideoPlayback.current
        if (videoIndex !in channels.indices) videoIndex = VideoPlayback.index.value.coerceIn(0, channels.lastIndex)
        return channels.getOrNull(videoIndex)
    }

    fun nextChannel() = onMain {
        val channels = VideoPlayback.channels.value
        if (channels.isNotEmpty() && showVideo) {
            videoIndex = (videoIndex + 1).mod(channels.size)
            stopVideo()
            startVideo()
        }
    }

    fun togglePlayback() = onMain {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun stopVideo() {
        player?.let { p ->
            runCatching { p.clearVideoSurface() }
            p.release()
        }
        player = null
    }

    fun tap(x: Float, y: Float) = onMain {
        val (which, px, py) = locate(x, y)
        selected = which
        val pane = panes.getOrNull(which) ?: return@onMain
        if (pane.video != null) { togglePlayback(); return@onMain }
        val view = pane.web ?: return@onMain
        val down = SystemClock.uptimeMillis()
        send(view, MotionEvent.ACTION_DOWN, px, py, down)
        send(view, MotionEvent.ACTION_UP, px, py, down)
    }

    fun scroll(dx: Float, dy: Float) = onMain {
        val pane = panes.getOrNull(selected) ?: return@onMain
        val web = pane.web ?: return@onMain
        val cx = web.width * .5f
        val cy = web.height * .5f
        val stepX = dx.coerceIn(-web.width * .35f, web.width * .35f)
        val stepY = dy.coerceIn(-web.height * .35f, web.height * .35f)
        if (web.width < 8 || web.height < 8) return@onMain
        val down = SystemClock.uptimeMillis()
        send(web, MotionEvent.ACTION_DOWN, cx, cy, down)
        send(web, MotionEvent.ACTION_MOVE,
            (cx - stepX).coerceIn(2f, web.width - 2f),
            (cy - stepY).coerceIn(2f, web.height - 2f), down)
        send(web, MotionEvent.ACTION_UP,
            (cx - stepX).coerceIn(2f, web.width - 2f),
            (cy - stepY).coerceIn(2f, web.height - 2f), down)
    }

    fun back() = onMain {
        val web = panes.getOrNull(selected)?.web ?: return@onMain
        if (web.canGoBack()) web.goBack()
    }

    fun reload() = onMain { panes.getOrNull(selected)?.web?.reload() }

    private fun locate(x: Float, y: Float): Triple<Int, Float, Float> {
        if (panes.size < 2) return Triple(0, x, y)
        val half = if (horizontal) screenWidth / 2f else screenHeight / 2f
        val which = if ((if (horizontal) x else y) < half) 0 else 1
        val localX = if (horizontal && which == 1) x - half else x
        val localY = if (!horizontal && which == 1) y - half else y
        return Triple(which, localX, localY)
    }

    private fun send(web: WebView, action: Int, x: Float, y: Float, down: Long) {
        if (web.width <= 0 || web.height <= 0) return
        val event = MotionEvent.obtain(
            down, SystemClock.uptimeMillis(), action,
            x.coerceIn(1f, web.width - 1f), y.coerceIn(1f, web.height - 1f), 0
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        web.dispatchTouchEvent(event)
        event.recycle()
    }

    fun detach() = onMain { detachNow() }

    private fun detachNow() {
        stopVideo()
        panes.forEach { pane ->
            pane.web?.let { web ->
                pane.frame.removeView(web)
                web.stopLoading()
                web.destroy()
            }
        }
        panes.clear()
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
        root = null
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else main.post { block() }
    }
}
