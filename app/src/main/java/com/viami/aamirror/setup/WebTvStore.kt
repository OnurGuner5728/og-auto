package com.onurguner.ogauto.setup

import android.content.Context

/** The Web TV address set on the phone, plus a flag so the car reloads it once after a change. */
object WebTvStore {
    private const val PREFS = "webtv"
    private const val KEY_URL = "url"
    private const val KEY_CHANGED = "changed"

    fun url(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, "").orEmpty()

    fun setUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url.trim()).putBoolean(KEY_CHANGED, true).apply()
    }

    /** True once after [setUrl] (and on first use), so the car loads the new address. */
    fun consumeChanged(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val changed = prefs.getBoolean(KEY_CHANGED, true)
        if (changed) prefs.edit().putBoolean(KEY_CHANGED, false).apply()
        return changed
    }
}
