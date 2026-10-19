package com.onurguner.ogauto.car

import android.content.Context

/** Favorites are opt-in, stored only for this experimental app package. */
internal object FavoritesStore {
    private const val PREFS = "favorite_apps"
    private const val KEY = "packages"
    fun get(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun toggle(context: Context, pkg: String) {
        val saved = get(context).toMutableSet()
        if (!saved.add(pkg)) saved.remove(pkg)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, saved).apply()
    }
}
