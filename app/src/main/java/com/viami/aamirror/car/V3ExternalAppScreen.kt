package com.onurguner.ogauto.car

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.onurguner.ogauto.R
import com.onurguner.ogauto.input.MirrorAccessibilityService

/**
 * Experimental non-MediaProjection Android secondary display.
 *
 * Public displays may launch activities from other packages, if the phone
 * has FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS and its security policy permits
 * that activity. The app might refuse, appear on the phone or show black.
 * This is not a universal Android Auto or Android capability. No root bypass.
 */
class V3ExternalAppScreen(
    carContext: CarContext,
    private val targetPackage: String,
    private val appLabel: String,
) : Screen(carContext), DefaultLifecycleObserver {
    private var virtual: VirtualDisplay? = null
    private var displayId = -1
    private var width = 0
    private var height = 0
    private val sink = object : SurfaceSink {
        override fun onAttach(container: SurfaceContainer) {
            if (container.surface?.isValid != true) return
            release()
            width = container.width
            height = container.height
            if (Build.VERSION.SDK_INT < 30 ||
                !carContext.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS
                )
            ) {
                notifyUser(R.string.v3_external_not_supported)
                return
            }
            try {
                val manager = carContext.getSystemService(DisplayManager::class.java)
                val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                val vd = manager.createVirtualDisplay(
                    "OgAuto-V3-App", width, height, container.dpi, container.surface, flags,
                )
                virtual = vd
                displayId = vd.display.displayId
                val pm = carContext.packageManager
                val intent = pm.getLaunchIntentForPackage(targetPackage)
                    ?: pm.getLeanbackLaunchIntentForPackage(targetPackage)
                requireNotNull(intent) { "No launch activity" }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                val options = ActivityOptions.makeBasic().apply { setLaunchDisplayId(displayId) }
                carContext.applicationContext.startActivity(intent, options.toBundle())
                notifyUser(R.string.v3_external_testing)
            } catch (e: Exception) {
                Log.w("V3ExternalApp", "secondary display launch rejected: $targetPackage", e)
                release()
                notifyUser(R.string.v3_external_failed)
            }
        }

        override fun onDetach() = release()

        override fun onTap(x: Float, y: Float) {
            val display = displayId
            if (display >= 0 && !MirrorAccessibilityService.tapOnDisplay(display, x, y)) {
                notifyUser(R.string.car_accessibility_missing)
            }
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            val display = displayId
            if (display < 0 || width < 5 || height < 5) return
            val x = width / 2f
            val y = height / 2f
            val dx = distanceX.coerceIn(-width * .4f, width * .4f)
            val dy = distanceY.coerceIn(-height * .4f, height * .4f)
            MirrorAccessibilityService.swipeOnDisplay(
                display, x, y,
                (x - dx).coerceIn(1f, width - 1f),
                (y - dy).coerceIn(1f, height - 1f),
            )
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            onScroll(velocityX * .12f, velocityY * .12f)
        }
    }

    init { lifecycle.addObserver(this) }

    override fun onStart(owner: LifecycleOwner) { SurfaceRouter.setSink(sink) }

    override fun onStop(owner: LifecycleOwner) { release() }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_menu) { screenManager.popToRoot() })
            .addAction(action(R.drawable.ic_reload) {
                val current = SurfaceRouter.container
                if (current != null) sink.onAttach(current)
            })
            .addAction(action(R.drawable.ic_cast) {
                screenManager.push(MirrorScreen(carContext, mediaControls = true))
            })
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .withPan()
            .build()
    }

    private fun notifyUser(res: Int) =
        CarToast.makeText(carContext, carContext.getString(res), CarToast.LENGTH_LONG).show()

    private fun release() {
        displayId = -1
        virtual?.release()
        virtual = null
    }

    private fun action(icon: Int, click: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
            .setOnClickListener(click)
            .build()
}
