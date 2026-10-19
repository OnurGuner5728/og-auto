package com.onurguner.ogauto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.onurguner.ogauto.R
import com.onurguner.ogauto.core.MenuLayout
import com.onurguner.ogauto.core.MirrorSettings
import com.onurguner.ogauto.setup.SettingsStore

/** Root menu of OgAuto: pick a mode, or open the settings. */
class HomeScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    /**
     * One entry of the menu. Grid cells truncate long titles, so they carry
     * a short label of their own.
     */
    private class Mode(
        val titleRes: Int,
        val shortTitleRes: Int,
        val iconRes: Int,
        val open: () -> Unit,
    )

    private val modes = listOf(
        Mode(R.string.menu_apps, R.string.menu_apps_short, R.drawable.ic_grid) {
            screenManager.push(AppsScreen(carContext))
        },
        Mode(R.string.menu_mirror, R.string.menu_mirror_short, R.drawable.ic_app) {
            screenManager.push(MirrorScreen(carContext))
        },
        Mode(R.string.menu_video, R.string.menu_video_short, R.drawable.ic_movie) {
            screenManager.push(VideoScreen(carContext))
        },
        Mode(R.string.menu_browser, R.string.menu_browser_short, R.drawable.ic_globe) {
            screenManager.push(BookmarksScreen(carContext))
        },
        Mode(R.string.menu_youtube, R.string.menu_youtube_short, R.drawable.ic_cast) {
            screenManager.push(YouTubeScreen(carContext))
        },
        Mode(R.string.menu_vypn, R.string.menu_vypn_short, R.drawable.ic_movie) {
            openVypn()
        },
    )

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        SurfaceRouter.register(carContext)
        SettingsStore.load(carContext)
    }

    override fun onStart(owner: LifecycleOwner) {
        SurfaceRouter.setSink(null)
        // The settings screen may have changed the layout while we were away.
        invalidate()
    }

    override fun onGetTemplate(): Template =
        if (MirrorSettings.menuLayout == MenuLayout.GRID) gridTemplate() else listTemplate()

    private fun gridTemplate(): Template {
        val items = ItemList.Builder()
        for (mode in modes) {
            items.addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(mode.shortTitleRes))
                    .setImage(icon(mode.iconRes))
                    .setOnClickListener {
                        if (carContext.getSharedPreferences("consent", 0).getBoolean("accepted_v1", false)) mode.open()
                        else androidx.car.app.CarToast.makeText(carContext, "OgAuto: telefonda kullanım onayını ver / Accept the notice on your phone", androidx.car.app.CarToast.LENGTH_LONG).show()
                    }
                    .build()
            )
        }
        return GridTemplate.Builder()
            .setSingleList(items.build())
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(settingsStrip())
            .build()
    }

    private fun listTemplate(): Template {
        val items = ItemList.Builder()
        for (mode in modes) {
            items.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(mode.titleRes))
                    .setImage(icon(mode.iconRes))
                    .setOnClickListener {
                        if (carContext.getSharedPreferences("consent", 0).getBoolean("accepted_v1", false)) mode.open()
                        else androidx.car.app.CarToast.makeText(carContext, "OgAuto: telefonda kullanım onayını ver / Accept the notice on your phone", androidx.car.app.CarToast.LENGTH_LONG).show()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(items.build())
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(settingsStrip())
            .build()
    }

    private fun settingsStrip(): ActionStrip =
        ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_settings))
                    .setOnClickListener { screenManager.push(SettingsScreen(carContext)) }
                    .build()
            )
            .build()

    /**
     * VYPN shortcut: start the VYPN app on the phone and go straight to the
     * mirror, so it shows on the car screen. The TV build is used when that
     * is the one installed.
     */
    private fun openVypn() {
        val pm = carContext.packageManager
        val intent = VYPN_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?: VYPN_PACKAGES.firstNotNullOfOrNull { pm.getLeanbackLaunchIntentForPackage(it) }
        if (intent == null) {
            androidx.car.app.CarToast.makeText(
                carContext, carContext.getString(R.string.vypn_missing), androidx.car.app.CarToast.LENGTH_LONG,
            ).show()
            return
        }
        try {
            carContext.applicationContext.startActivity(
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            androidx.car.app.CarToast.makeText(
                carContext, carContext.getString(R.string.apps_launch_failed), androidx.car.app.CarToast.LENGTH_LONG,
            ).show()
            return
        }
        // The user may start capture explicitly after launching VYPN.
        screenManager.push(MirrorScreen(carContext, mediaControls = true))
    }

    private fun icon(iconRes: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build()

    private companion object {
        val VYPN_PACKAGES = listOf("net.vypn.app", "net.vypn.tv", "net.vypn.app.tv")
    }
}
