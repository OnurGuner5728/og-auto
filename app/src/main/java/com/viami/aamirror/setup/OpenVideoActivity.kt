package com.onurguner.ogauto.setup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import com.onurguner.ogauto.R
import com.onurguner.ogauto.car.SurfaceRouter
import com.onurguner.ogauto.core.Channel
import com.onurguner.ogauto.core.M3uParser
import com.onurguner.ogauto.video.PlaylistStore
import com.onurguner.ogauto.video.VideoPlayback
import kotlinx.coroutines.launch

/**
 * Entry point for "open with OgAuto": IPTV apps' external-player option
 * (the MX Player / VLC style intent), a shared link, or a tapped video
 * link. The stream is handed to the car player; nothing plays on the phone.
 * MX Player extras are honoured: "title", "headers" (alternating keys and
 * values) and "position".
 */
class OpenVideoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val url = extractUrl(intent)
        if (url == null) {
            Toast.makeText(this, R.string.video_no_link, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val title = intent?.getStringExtra("title")
            ?: intent?.getStringExtra(Intent.EXTRA_TITLE)
            ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: url.substringBefore('?').substringAfterLast('/').ifEmpty { url }
        val headers = headersFrom(intent)

        if (M3uParser.isPlaylistUrl(url)) {
            PlaylistStore.setUrl(this, url)
            lifecycleScope.launch {
                try {
                    val list = PlaylistStore.download(this@OpenVideoActivity, url)
                    VideoPlayback.setChannels(this@OpenVideoActivity, list)
                    Toast.makeText(
                        this@OpenVideoActivity,
                        getString(R.string.video_playlist_loaded, list.size),
                        Toast.LENGTH_LONG,
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@OpenVideoActivity,
                        getString(R.string.video_playlist_failed, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                finish()
            }
            return
        }

        // content:// links are readable through the grant this intent carries;
        // keep it for the playback, which runs in this same process.
        intent?.data?.takeIf { it.scheme == "content" }?.let {
            runCatching {
                grantUriPermission(packageName, it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        VideoPlayback.playSingle(this, Channel(title = title, url = url, headers = headers))
        val inCar = SurfaceRouter.container != null
        Toast.makeText(
            this,
            if (inCar) getString(R.string.video_sent_to_car, title) else getString(R.string.video_open_car),
            Toast.LENGTH_LONG,
        ).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun extractUrl(intent: Intent?): String? {
        intent ?: return null
        intent.data?.toString()?.let { if (M3uParser.looksLikeUrl(it)) return it }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toString()
            } else {
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.toString()
            }
            ?: return null
        return Regex("""(https?|rtsp|content)://\S+""").find(text)?.value?.trimEnd(')', ']', '.', ',')
    }

    private fun headersFrom(intent: Intent?): Map<String, String> {
        val raw = intent?.getStringArrayExtra("headers") ?: return emptyMap()
        val map = linkedMapOf<String, String>()
        var i = 0
        while (i + 1 < raw.size) {
            val key = raw[i].trim()
            if (key.isNotEmpty()) {
                map[if (key.equals("user-agent", true)) "User-Agent" else key] = raw[i + 1]
            }
            i += 2
        }
        return map
    }
}
