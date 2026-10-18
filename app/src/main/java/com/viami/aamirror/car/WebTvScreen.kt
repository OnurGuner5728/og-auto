package com.onurguner.ogauto.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.onurguner.ogauto.R
import com.onurguner.ogauto.browser.WebTvDisplay
import com.onurguner.ogauto.core.UrlResolver
import com.onurguner.ogauto.setup.WebTvStore

/**
 * The user's own stream site on the car display. The address is set on the
 * phone; the page renders on the car surface like YouTube TV, so its video
 * plays natively. ⌂ returns to that address after browsing away.
 */
class WebTvScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val url = WebTvStore.url(carContext)
        if (url.isBlank()) {
            CarToast.makeText(carContext, carContext.getString(R.string.webtv_not_set), CarToast.LENGTH_LONG).show()
        } else if (WebTvStore.consumeChanged(carContext)) {
            WebTvDisplay.loadUrl(UrlResolver.resolve(url))
        }
        SurfaceRouter.setSink(WebSink.of(carContext, WebTvDisplay))
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_menu) { screenManager.pop() })
            .addAction(action(R.drawable.ic_back) { WebTvDisplay.goBack() })
            .addAction(action(R.drawable.ic_home) {
                WebTvStore.url(carContext).takeIf { it.isNotBlank() }
                    ?.let { WebTvDisplay.loadUrl(UrlResolver.resolve(it)) }
            })
            .addAction(action(R.drawable.ic_reload) { WebTvDisplay.reload() })
            .build()
        return NavigationTemplate.Builder().setActionStrip(strip).withPan().build()
    }

    private fun action(iconRes: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setOnClickListener(onClick)
            .build()
}
