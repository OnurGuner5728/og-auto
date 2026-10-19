package com.onurguner.ogauto.car

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
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
import com.onurguner.ogauto.core.CarLaunchPolicy
import com.onurguner.ogauto.core.MirrorGateway
import com.onurguner.ogauto.core.RecentApps

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
        val apps = RecentApps.order(carContext, loadApps()) { it.pkg }
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        }.getOrDefault(12).coerceAtLeast(4)
        val perPage = (limit - 3).coerceAtLeast(1)
        val start = page * perPage
        val items = ItemList.Builder()
        if (page > 0) {
            items.addItem(navItem(R.string.video_prev_page, R.drawable.ic_skip_prev) { screenManager.pop() })
        } else {
            items.addItem(navItem(R.string.apps_manual_mirror, R.drawable.ic_cast) {
                screenManager.push(MirrorScreen(carContext))
            })
        }
        apps.drop(start).take(perPage).forEach { app ->
            items.addItem(
                GridItem.Builder()
                    .setTitle(app.label)
                    .setImage(app.icon)
                    .setOnClickListener { launch(app) }
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
    }

    private fun launch(app: AppEntry) {
        val intent = carContext.packageManager.getLaunchIntentForPackage(app.pkg)
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
        // Never start a capture merely because the user launched a phone app.
        if (CarLaunchPolicy.returnToExistingMirror(MirrorGateway.state.value.projection)) {
            screenManager.popTo(MIRROR_MARKER)
            if (screenManager.top !is MirrorScreen) screenManager.push(MirrorScreen(carContext))
        } else {
            CarToast.makeText(carContext, carContext.getString(R.string.apps_started_on_phone), CarToast.LENGTH_LONG).show()
        }
    }

    private fun loadApps(): List<AppEntry> {
        cache?.let { return it }
        val pm = carContext.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != carContext.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                AppEntry(
                    label = info.loadLabel(pm).toString(),
                    pkg = info.packageName,
                    icon = CarIcon.Builder(IconCompat.createWithBitmap(toBitmap(info.loadIcon(pm)))).build(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        cache = list
        return list
    }

    private fun toBitmap(d: Drawable): Bitmap {
        val size = 96
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
        private var cache: List<AppEntry>? = null
    }
}
