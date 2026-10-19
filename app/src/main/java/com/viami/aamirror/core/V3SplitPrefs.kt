package com.onurguner.ogauto.core

import android.content.Context
import java.net.URI

/** URLs of two independent OgAuto WebView panes; not third-party Android activities. */
object V3SplitPrefs {
    const val DEFAULT_LEFT = "https://www.google.com/maps"
    const val DEFAULT_RIGHT = "https://www.youtube.com/tv"
    private const val PREFS = "v3_split_web"
    private const val LEFT = "left_url"
    private const val RIGHT = "right_url"

    fun isSafeWebUrl(url: String): Boolean = runCatching {
        val parsed = URI(url.trim())
        (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
            !parsed.host.isNullOrBlank() && parsed.rawUserInfo == null
    }.getOrDefault(false)

    fun left(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LEFT, DEFAULT_LEFT)?.takeIf(::isSafeWebUrl) ?: DEFAULT_LEFT

    fun right(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RIGHT, DEFAULT_RIGHT)?.takeIf(::isSafeWebUrl) ?: DEFAULT_RIGHT

    fun save(context: Context, left: String, right: String): Boolean {
        if (!isSafeWebUrl(left) || !isSafeWebUrl(right)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LEFT, left.trim()).putString(RIGHT, right.trim()).apply()
        return true
    }
}
