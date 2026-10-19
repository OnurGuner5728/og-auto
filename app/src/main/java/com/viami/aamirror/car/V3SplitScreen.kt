package com.onurguner.ogauto.car

import androidx.car.app.CarContext
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

/** Two independent OgAuto panes, with an explicit exit button. */
class V3SplitScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private val sink = object : SurfaceSink {
        override fun onAttach(container: SurfaceContainer) {
            val surface = container.surface ?: return
            V3SplitDisplay.attach(carContext, surface, container.width, container.height, container.dpi)
        }
        override fun onDetach() = V3SplitDisplay.detach()
        override fun onTap(x: Float, y: Float) = V3SplitDisplay.tap(x, y)
        override fun onScroll(distanceX: Float, distanceY: Float) =
            V3SplitDisplay.scroll(distanceX, distanceY)
        override fun onFling(velocityX: Float, velocityY: Float) =
            V3SplitDisplay.scroll(velocityX * 0.10f, velocityY * 0.10f)
    }

    init { lifecycle.addObserver(this) }

    override fun onStart(owner: LifecycleOwner) {
        SurfaceRouter.setSink(sink)
    }

    override fun onStop(owner: LifecycleOwner) {
        V3SplitDisplay.detach()
        CameraResumeStore.mark(carContext, "split")
    }

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_menu) { screenManager.pop() })
            .addAction(action(R.drawable.ic_rotate) { V3SplitDisplay.toggleOrientation() })
            .addAction(action(R.drawable.ic_grid) { V3SplitDisplay.swapPanels() })
            .addAction(action(R.drawable.ic_movie) { V3SplitDisplay.toggleSecond(carContext) })
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(actions)
            .withPan(
                action(R.drawable.ic_back) { V3SplitDisplay.back() },
                action(R.drawable.ic_reload) { V3SplitDisplay.reload() },
                action(R.drawable.ic_skip_next) { V3SplitDisplay.nextChannel() },
            )
            .build()
    }

    private fun action(icon: Int, click: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
            .setOnClickListener(click)
            .build()
}
