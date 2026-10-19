package com.onurguner.ogauto.car

import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.onurguner.ogauto.R
import com.onurguner.ogauto.input.MirrorAccessibilityService
import com.onurguner.ogauto.input.PhoneKeys

/**
 * Standardized, selectable shortcut control for third-party phone apps.
 * The app's real private UI cannot be embedded without a screen-capture
 * session. Media keys are best-effort: the target must expose MediaSession.
 */
class V3AppControlScreen(
    carContext: CarContext,
    private val packageName: String,
    private val label: String,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        fun add(title: Int, click: () -> Unit) {
            items.addItem(Row.Builder().setTitle(carContext.getString(title))
                .setOnClickListener(click).build())
        }
        add(R.string.v3_app_open) { openPhoneApp() }
        add(R.string.v3_app_play) { PhoneKeys.playPause(carContext) }
        add(R.string.v3_app_previous) { PhoneKeys.previous(carContext) }
        add(R.string.v3_app_next) { PhoneKeys.next(carContext) }
        add(R.string.v3_app_back) {
            if (!MirrorAccessibilityService.pressBack()) {
                CarToast.makeText(carContext, carContext.getString(R.string.car_accessibility_missing), CarToast.LENGTH_LONG).show()
            }
        }
        add(R.string.v3_app_legacy_capture) { screenManager.push(MirrorScreen(carContext, mediaControls = true)) }
        add(R.string.v3_app_exit) { screenManager.popToRoot() }
        return ListTemplate.Builder()
            .setTitle(label)
            .setSingleList(items.build())
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun openPhoneApp() {
        val pm = carContext.packageManager
        val launch = pm.getLaunchIntentForPackage(packageName)
            ?: pm.getLeanbackLaunchIntentForPackage(packageName)
        try {
            requireNotNull(launch)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            carContext.applicationContext.startActivity(launch)
            CarToast.makeText(carContext, carContext.getString(R.string.v3_app_launched), CarToast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w("V3AppControl", "could not launch $packageName", e)
            CarToast.makeText(carContext, carContext.getString(R.string.apps_launch_failed), CarToast.LENGTH_LONG).show()
        }
    }
}
