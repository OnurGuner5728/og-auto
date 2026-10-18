package com.onurguner.ogauto.video

import android.content.Context
import com.onurguner.ogauto.core.Channel
import com.onurguner.ogauto.core.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The user's IPTV playlist: its URL in SharedPreferences, the last download
 * cached in app storage so the car list works offline and starts instantly.
 */
object PlaylistStore {

    private const val PREFS = "video"
    private const val KEY_URL = "playlistUrl"
    private const val CACHE = "playlist.m3u"
    private const val MAX_BYTES = 30L * 1024 * 1024

    fun url(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, "").orEmpty()

    fun setUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url.trim()).apply()
    }

    /** Cached channels, or empty. Cheap enough for the main thread on small lists. */
    fun cached(context: Context): List<Channel> {
        val file = File(context.filesDir, CACHE)
        if (!file.isFile) return emptyList()
        return runCatching { M3uParser.parse(file.readText()) }.getOrDefault(emptyList())
    }

    /** Downloads [url], caches it and returns the parsed channels. Throws on failure. */
    suspend fun download(context: Context, url: String): List<Channel> = withContext(Dispatchers.IO) {
        val text = fetch(url)
        val channels = M3uParser.parse(text)
        require(channels.isNotEmpty()) { "Listede oynatılabilir kanal bulunamadı" }
        File(context.filesDir, CACHE).writeText(text)
        channels
    }

    private fun fetch(address: String): String {
        var target = address
        repeat(5) {
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "OgAuto/0.2 (Linux; Android)")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: error("HTTP $code")
                    target = URL(URL(target), location).toString()
                    return@repeat
                }
                if (code !in 200..299) error("HTTP $code")
                conn.inputStream.use { input ->
                    val bytes = input.readNBytesCompat(MAX_BYTES)
                    return String(bytes, Charsets.UTF_8)
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Çok fazla yönlendirme")
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) error("Liste çok büyük")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
