package com.onurguner.ogauto.setup

import android.content.Context
import com.onurguner.ogauto.core.MenuLayout
import com.onurguner.ogauto.core.MirrorSettings

/**
 * SharedPreferences behind [MirrorSettings], which stays a plain in-memory
 * holder so the renderers can read it without a Context.
 */
object SettingsStore {

    private const val PREFS = "settings"
    // v2 keys: 0.2.0 changed both defaults to on, so values saved by 0.1.0
    // (where "off" was simply the untouched default) are not carried over.
    private const val KEY_FILL = "fillScreen_v2"
    private const val KEY_LANDSCAPE = "autoLandscape"
    private const val KEY_LAYOUT = "menuLayout"
    private const val KEY_SW_DECODER = "softwareDecoder"
    private const val KEY_MAX_FPS = "maxFps"

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        MirrorSettings.fillScreen = prefs.getBoolean(KEY_FILL, true)
        MirrorSettings.autoLandscape = prefs.getBoolean(KEY_LANDSCAPE, true)
        MirrorSettings.menuLayout = MenuLayout.fromKey(prefs.getString(KEY_LAYOUT, null))
        MirrorSettings.preferSoftwareDecoder = prefs.getBoolean(KEY_SW_DECODER, false)
        MirrorSettings.maxFps = prefs.getInt(KEY_MAX_FPS, 30)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FILL, MirrorSettings.fillScreen)
            .putBoolean(KEY_LANDSCAPE, MirrorSettings.autoLandscape)
            .putString(KEY_LAYOUT, MirrorSettings.menuLayout.key)
            .putBoolean(KEY_SW_DECODER, MirrorSettings.preferSoftwareDecoder)
            .putInt(KEY_MAX_FPS, MirrorSettings.maxFps)
            .apply()
    }
}
