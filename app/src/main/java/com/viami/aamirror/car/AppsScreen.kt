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
    private val showAll: Boolean = false,
) : Screen(carContext) {

    private data class AppEntry(val label: String, val pkg: String, val icon: CarIcon)

    override fun onGetTemplate(): Template {
        val started = PerfTrace.now()
        val favorites = FavoritesStore.get(carContext)
        // Stable sorting keeps recently opened apps above alphabetical peers,
        // but all explicitly selected favorites still come first.
        val recentlyOrdered = RecentApps.order(carContext, loadApps()) { it.pkg }
        val ranked = recentlyOrdered.sortedWith(compareBy<AppEntry> { if (it.pkg in favorites) 0 else 1 })
        val apps = if (showAll) ranked else V3SelectedApps.filter(carContext, ranked) { it.pkg }
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        }.getOrDefault(12).coerceAtLeast(4)
        val perPage = (limit - 5).coerceAtLeast(1)
        val start = page * perPage
        val items = ItemList.Builder()
        if (page > 0) {
            items.addItem(navItem(R.string.video_prev_page, R.drawable.ic_skip_prev) { screenManager.pop() })
        } else {
            items.addItem(navItem(R.string.apps_manual_mirror, R.drawable.ic_cast) {
                screenManager.push(MirrorScreen(carContext))
            })
        }
        if (page == 0) {
            items.addItem(navItem(if (showAll) R.string.v3_selected_only else R.string.v3_show_all, R.drawable.ic_grid) {
                screenManager.push(AppsScreen(carContext, 0, !showAll))
            })
            items.addItem(navItem(R.string.favorites_edit, R.drawable.ic_grid) {
                choosingFavorites = !choosingFavorites
                invalidate()
            })
        }
        if (apps.isEmpty() && loading) {
            items.addItem(navItem(R.string.apps_loading, R.drawable.ic_grid) { })
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
                screenManager.push(AppsScreen(carContext, page + 1, showAll))
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
        val intent = (carContext.packageManager.getLaunchIntentForPackage(app.pkg)
            ?: carContext.packageManager.getLeanbackLaunchIntentForPackage(app.pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            requireNotNull(intent)
            // Allowed from the background: OgAuto holds an enabled
            // accessibility service / overlay permission.
            carContext.applicationContext.startActivity(intent)
            RecentApps.record(carContext, app.pkg)
        } catch (e: Exception) {
            Log.w(TAG, "launch ${app.pkg} failed", e)
            CarToast.makeText(carContext, carContext.getString(R.string.apps_launch_failed), CarToast.LENGTH_LONG).show()
            return
        }
        // Never start capture merely because a phone app was launched.
        // Already approved projection can continue to be reused.
        if (CarLaunchPolicy.returnToExistingMirror(MirrorGateway.state.value.projection)) {
            screenManager.popTo(MIRROR_MARKER)
            if (screenManager.top !is MirrorScreen) screenManager.push(MirrorScreen(carContext))
        } else {
            screenManager.push(V3AppControlScreen(carContext, app.pkg, app.label))
        }
    }

    /** Never decode all launcher icons synchronously in onGetTemplate(). */
    private fun loadApps(): List<AppEntry> {
        cache?.let { return it }
        synchronized(AppsScreen::class.java) {
            if (loading) return emptyList()
            loading = true
        }
        val pm = carContext.packageManager
        val myPackage = carContext.packageName
        executor.execute {
            val result = runCatching {
                val queries = listOf(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                )
                queries.flatMap { pm.queryIntentActivities(it, PackageManager.MATCH_ALL) }
                    .asSequence().map { it.activityInfo }
                    .filter { it.packageName != myPackage }.distinctBy { it.packageName }
                    .map { info ->
                        AppEntry(info.loadLabel(pm).toString(), info.packageName,
                            CarIcon.Builder(IconCompat.createWithBitmap(toBitmap(info.loadIcon(pm)))).build())
                    }
                    .sortedBy { it.label.lowercase() }.toList()
            }.onFailure { Log.w(TAG, "async app scan failed", it) }.getOrDefault(emptyList())
            cache = result
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
        @Volatile private var choosingFavorites: Boolean = false

        /** Discard icon bitmaps when the system reports memory pressure. */
        fun clearCache() { cache = null }
        @Volatile private var loading: Boolean = false
        private val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ogauto-app-icons").apply { isDaemon = true }
        }
    }
}
