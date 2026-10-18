package com.onurguner.ogauto.video

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.onurguner.ogauto.core.AspectFit
import com.onurguner.ogauto.core.Channel
import com.onurguner.ogauto.core.MirrorSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Native video / IPTV playback on the car display, the way Fermata and
 * XtreamPlayer do it: the stream is decoded straight onto the car surface
 * instead of being played on the phone and screen-captured. No
 * MediaProjection, no second copy of every frame, nothing for a capture
 * pipeline to freeze on.
 *
 * Rendering reuses the browser technique: the car Surface backs a private
 * VirtualDisplay and a Presentation on it holds the SurfaceView the player
 * decodes into. Everything runs on the main thread.
 */
@OptIn(UnstableApi::class)
object VideoPlayback {

    private const val TAG = "VideoPlayback"
    private const val DEFAULT_UA = "OgAuto/0.2 (Linux; Android) ExoPlayer"
    private const val MAX_RETRIES = 4

    private val main = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var player: ExoPlayer? = null
    private var playerUsesSoftware = false

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var surfaceView: SurfaceView? = null
    private var statusView: TextView? = null
    private var frame: FrameLayout? = null
    private var surfaceSize = 0 to 0

    private var retries = 0

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    /** The loaded playlist (or the single stream that was handed to us). */
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _index = MutableStateFlow(-1)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _openRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted when a phone app handed us a stream: the car shows the player. */
    val openRequests: SharedFlow<Unit> = _openRequests

    val current: Channel?
        get() = _channels.value.getOrNull(_index.value)

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    // ---------------------------------------------------------------- input

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Replace the channel list (a playlist), optionally starting one. */
    fun setChannels(context: Context, list: List<Channel>, startIndex: Int = -1) = onMain {
        init(context)
        _channels.value = list
        if (startIndex in list.indices) playIndexNow(startIndex) else _index.value = -1
    }

    /** Play one stream handed over by another app (external player, share, link). */
    fun playSingle(context: Context, channel: Channel) = onMain {
        init(context)
        val list = _channels.value
        val existing = list.indexOfFirst { it.url == channel.url }
        if (existing >= 0) {
            playIndexNow(existing)
        } else {
            _channels.value = listOf(channel) + list
            playIndexNow(0)
        }
        _openRequests.tryEmit(Unit)
    }

    fun playIndex(i: Int) = onMain { playIndexNow(i) }

    fun next() = onMain {
        val n = _channels.value.size
        if (n > 0) playIndexNow((_index.value + 1).mod(n))
    }

    fun previous() = onMain {
        val n = _channels.value.size
        if (n > 0) playIndexNow((_index.value - 1).mod(n))
    }

    fun togglePause() = onMain {
        val p = player ?: return@onMain
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }

    fun retry() = onMain {
        val i = _index.value
        if (i >= 0) playIndexNow(i)
    }

    /** Stop everything (car disconnected). */
    fun stop() = onMain {
        detachNow()
        player?.release()
        player = null
        _status.value = null
    }

    /** The decoder preference changed: rebuild the player keeping position in the list. */
    fun applyDecoderPreference() = onMain {
        if (player == null || playerUsesSoftware == MirrorSettings.preferSoftwareDecoder) return@onMain
        val i = _index.value
        player?.release()
        player = null
        surfaceView?.let { buildPlayer().setVideoSurfaceView(it) }
        if (i >= 0) playIndexNow(i)
    }

    private fun playIndexNow(i: Int) {
        val channel = _channels.value.getOrNull(i) ?: return
        val context = appContext ?: return
        _index.value = i
        retries = 0
        val p = player ?: buildPlayer()
        val headers = channel.headers.toMutableMap()
        val ua = headers.remove("User-Agent") ?: DEFAULT_UA
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(headers)
        val sourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
        val item = MediaItem.Builder()
            .setUri(Uri.parse(channel.url))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(channel.title).build())
            .apply { mimeHint(channel.url)?.let { setMimeType(it) } }
            .build()
        Log.i(TAG, "play #$i ${channel.title}")
        setStatus("▶ ${channel.title} — yükleniyor…")
        p.setMediaSource(sourceFactory.createMediaSource(item))
        p.prepare()
        p.play()
    }

    /** Extension-less IPTV URLs: help the factory pick HLS when the URL says so. */
    private fun mimeHint(url: String): String? {
        val lower = url.lowercase().substringBefore('?')
        return when {
            lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            "m3u8" in url.lowercase() -> MimeTypes.APPLICATION_M3U8
            else -> null
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val context = appContext ?: error("init() not called")
        val software = MirrorSettings.preferSoftwareDecoder
        // Software first: the phone's hardware codecs are shared with the
        // Android Auto video encoder; on some chipsets a hardware decode
        // starves that encoder and the whole car display freezes.
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            if (software) infos.sortedBy { if (it.softwareOnly) 0 else 1 } else infos
        }
        val renderers = DefaultRenderersFactory(context)
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val p = ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        p.addListener(listener)
        player = p
        playerUsesSoftware = software
        return p
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> current?.let { setStatus("⏳ ${it.title}") }
                Player.STATE_READY -> { retries = 0; setStatus(null) }
                Player.STATE_ENDED -> setStatus("■ Bitti")
                else -> Unit
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) = layoutVideo(videoSize)

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "playback error ${error.errorCodeName}", error)
            val p = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            if (retries < MAX_RETRIES) {
                retries++
                setStatus("⚠ Yeniden bağlanıyor ($retries/$MAX_RETRIES)…")
                main.postDelayed({ player?.prepare(); player?.play() }, 1500L * retries)
            } else {
                setStatus("⚠ Oynatılamadı: ${error.errorCodeName}. ↻ ile tekrar dene veya ayarlardan çözücüyü değiştir.")
            }
        }
    }

    // ------------------------------------------------------------ rendering

    fun attach(context: Context, surface: Surface, width: Int, height: Int, densityDpi: Int) = onMain {
        init(context)
        detachNow()
        surfaceSize = width to height
        val manager = context.getSystemService(DisplayManager::class.java)
        val display = manager.createVirtualDisplay(
            "OgAutoVideo", width, height, densityDpi, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        val shown = Presentation(context, display.display)
        val root = FrameLayout(shown.context).apply { setBackgroundColor(Color.BLACK) }
        val sv = SurfaceView(shown.context)
        root.addView(sv, FrameLayout.LayoutParams(width, height, Gravity.CENTER))
        val text = TextView(shown.context).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, height / 18f)
            setPadding(height / 30, height / 60, height / 30, height / 60)
        }
        root.addView(
            text,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply { setMargins(height / 30, 0, 0, height / 20) },
        )
        shown.setContentView(root)
        shown.show()
        virtualDisplay = display
        presentation = shown
        surfaceView = sv
        statusView = text
        frame = root
        (player ?: buildPlayer()).setVideoSurfaceView(sv)
        player?.videoSize?.let { layoutVideo(it) }
        renderStatus()
        if (_index.value < 0 && _channels.value.isEmpty()) {
            setStatus("Telefonda bir yayını OgAuto ile aç (harici oynatıcı / paylaş) veya ayarlardan M3U listesi ekle.")
        }
    }

    fun detach() = onMain { detachNow() }

    private fun detachNow() {
        surfaceView?.let { player?.clearVideoSurfaceView(it) }
        presentation?.dismiss()
        virtualDisplay?.release()
        presentation = null
        virtualDisplay = null
        surfaceView = null
        statusView = null
        frame = null
    }

    /** Fit (letterbox) or fill (center crop), following the car fill setting. */
    private fun layoutVideo(size: VideoSize) {
        val sv = surfaceView ?: return
        val (w, h) = surfaceSize
        if (size.width <= 0 || size.height <= 0 || w <= 0 || h <= 0) return
        val videoW = (size.width * size.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
        val videoH = size.height
        val lp = sv.layoutParams as FrameLayout.LayoutParams
        if (MirrorSettings.fillScreen) {
            val scale = maxOf(w.toFloat() / videoW, h.toFloat() / videoH)
            lp.width = (videoW * scale).toInt()
            lp.height = (videoH * scale).toInt()
        } else {
            val fit = AspectFit.fit(videoW, videoH, w, h)
            lp.width = fit.width
            lp.height = fit.height
        }
        lp.gravity = Gravity.CENTER
        sv.layoutParams = lp
    }

    private fun setStatus(text: String?) {
        _status.value = text
        renderStatus()
    }

    private fun renderStatus() {
        val view = statusView ?: return
        val text = _status.value
        view.text = text.orEmpty()
        view.visibility = if (text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }
}
