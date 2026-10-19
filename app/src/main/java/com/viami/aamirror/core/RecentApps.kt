package com.onurguner.ogauto.core

import android.content.Context

/** Five most recently launched apps appear first, without extra permissions. */
object RecentApps {
    private const val PREFS = "launcher_usage"
    private const val KEY = "recent_packages"
    fun record(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = (listOf(packageName) + names(prefs.getString(KEY, ""))).distinct().take(5)
        prefs.edit().putString(KEY, list.joinToString("|")).apply()
    }
    private fun names(value: String?): List<String> =
        value.orEmpty().split('|').filter { it.isNotBlank() }

    fun <T> order(context: Context, items: List<T>, pkg: (T) -> String): List<T> {
        val recent = names(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ""))
        val rank = recent.withIndex().associate { it.value to it.index }
        return items.sortedWith(compareBy { rank[pkg(it)] ?: Int.MAX_VALUE })
    }
}
