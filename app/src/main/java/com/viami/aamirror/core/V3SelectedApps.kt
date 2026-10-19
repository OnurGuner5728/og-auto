package com.onurguner.ogauto.core

import android.content.Context

/** User-approved launcher shortcuts. No sensitive package-wide permission. */
object V3SelectedApps {
    private const val PREFS = "v3_selected_apps"
    private const val KEY = "selected"
    fun get(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun set(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, packages.toSet()).apply()
    }

    /** V3 is opt-in: an empty selection must never expose every phone app. */
    fun <T> filter(context: Context, items: List<T>, packageName: (T) -> String): List<T> {
        val selected = get(context)
        return items.filter { packageName(it) in selected }
    }
}
