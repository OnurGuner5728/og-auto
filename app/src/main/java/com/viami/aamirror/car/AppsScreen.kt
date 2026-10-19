package com.onurguner.ogauto.car

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import com.onurguner.ogauto.core.RecentApps
import com.onurguner.ogauto.core.PerfTrace
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.onurguner.ogauto.R
import com.onurguner.ogauto.car.FavoritesStore
import com.onurguner.ogauto.core.CarLaunchPolicy
import com.onurguner.ogauto.core.MirrorGateway
import com.onurguner.ogauto.core.V3SelectedApps

/**
 * Screen2Auto-style launcher: the phone's apps as a grid on the car
 * screen. Picking one starts it on the phone and returns to the mirror,
 * so it appears on the car display straight away.
 */
class AppsScreen(
    carContext: CarContext,
    private val page: Int = 0,
) : Screen(carContext) {

    private data class AppEntry(val label: String, val pkg: String, val icon: CarIcon)

    override fun onGetTemplate(): Template {
        val started = PerfTrace.now()
        val favorites = FavoritesStore.get(carContext)
        // Stable sorting keeps recently opened apps above alphabetical peers,
        // but all explicitly selected favorites still come first.
        val recentlyOrdered = RecentApps.order(carContext, loadApps()) { it.pkg }
        val ranked = recentlyOrdered.sortedWith(compareBy<AppEntry> { if (it.pkg in favorites) 0 else 1 })
        val apps = V3SelectedApps.filter(carContext, ranked) { it.pkg }
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        }.getOrDefault(12).coerceAtLeast(4)
        val perPage = (limit - 2).coerceAtLeast(1)
        val start = page * perPage
        val items = ItemList.Builder()
        if (page > 0) {
            items.addItem(navItem(R.string.video_prev_page, R.drawable.ic_skip_prev) { screenManager.pop() })
        } else if (apps.isNotEmpty()) {
            items.addItem(navItem(R.string.favorites_edit, R.drawable.ic_grid) {
                choosingFavorites = !choosingFavorites
                invalidate()
            })
        }
        if (apps.isEmpty()) {
            items.addItem(navItem(
                if (loading) R.string.apps_loading else R.string.v3_picker_empty,
                R.drawable.ic_grid,
            ) {
                CarToast.makeText(carContext, carContext.getString(R.string.v3_picker_phone_help), CarToast.LENGTH_LONG).show()
            })
        }
        apps.drop(start).take(perPage).forEach { app ->
            items.addItem(
                GridItem.Builder()
                    .setTitle(if (app.pkg in favorites) "★ " + app.label else app.label)
                    .setImage(app.icon)
                    .setOnClickListener {
                        if (choosingFavorites) {
                            FavoritesStore.toggle(carContext, app.pkg)
                            invalidate()
                        } else launch(app)
                    }
                    .build()
            )
        }
        if (start + perPage < apps.size) {
            items.addItem(navItem(R.string.video_next_page, R.drawable.ic_skip_next) {
                screenManager.push(AppsScreen(carContext, page + 1))
            })
        }
        return GridTemplate.Builder()
            .setSingleList(items.build())
            .setTitle(carContext.getString(R.string.menu_apps))
            .setHeaderAction(Action.BACK)
            .build()
            .also { PerfTrace.log("apps_template", started) }
    }

    private fun launch(app: AppEntry) {
        // Opening the car launcher never starts a phone activity or captures its screen.
        // The user chooses native media controls or an explicit secondary-display test.
        RecentApps.record(carContext, app.pkg)
        screenManager.push(V3AppControlScreen(carContext, app.pkg, app.label))
    }

    /** Never decode all launcher icons synchronously in onGetTemplate(). */
    private fun loadApps(): List<AppEntry> {
        val selected = V3SelectedApps.get(carContext)
        if (selected.isEmpty()) return emptyList()
        val requestedKey = selected.sorted().joinToString("|")
        if (cacheKey == requestedKey) cache?.let { return it }
        synchronized(AppsScreen::class.java) {
            if (loading) return emptyList()
            loading = true
        }
        val pm = carContext.packageManager
        executor.execute {
            val result = runCatching {
                val queries = listOf(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                )
                queries.flatMap { pm.queryIntentActivities(it, 0) }
                    .asSequence().map { it.activityInfo }
                    // Decode icons only for apps explicitly chosen on the phone.
                    .filter { it.packageName in selected }
                    .distinctBy { it.packageName }
                    .map { info ->
                        AppEntry(info.loadLabel(pm).toString(), info.packageName,
                            CarIcon.Builder(IconCompat.createWithBitmap(toBitmap(info.loadIcon(pm)))).build())
                    }.sortedBy { it.label.lowercase() }.toList()
            }.onFailure { Log.w(TAG, "async selected app scan failed", it) }
                .getOrDefault(emptyList())
            cache = result
            cacheKey = requestedKey
            loading = false
            Handler(Looper.getMainLooper()).post {
                if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) invalidate()
            }
        }
        return emptyList()
    }

    private fun toBitmap(d: Drawable): Bitmap {
        val size = 72
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }

    private fun navItem(titleRes: Int, iconRes: Int, onClick: () -> Unit): GridItem =
        GridItem.Builder()
            .setTitle(carContext.getString(titleRes))
            .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setOnClickListener(onClick)
            .build()

    companion object {
        private const val TAG = "AppsScreen"
        const val MIRROR_MARKER = "mirror"

        /** Icons are expensive to render; built once per process. */
        @Volatile private var cache: List<AppEntry>? = null
        @Volatile private var cacheKey: String? = null
        @Volatile private var choosingFavorites: Boolean = false

        /** Discard icon bitmaps when the system reports memory pressure. */
        fun clearCache() { cache = null; cacheKey = null }
        @Volatile private var loading: Boolean = false
        private val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ogauto-app-icons").apply { isDaemon = true }
        }
    }
}
