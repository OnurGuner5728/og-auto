package com.onurguner.ogauto.car

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
import androidx.lifecycle.lifecycleScope
import com.onurguner.ogauto.R
import com.onurguner.ogauto.core.AspectFit
import com.onurguner.ogauto.core.MirrorGateway
import com.onurguner.ogauto.core.MirrorSettings
import com.onurguner.ogauto.core.ProjectionStatus
import com.onurguner.ogauto.core.TouchMapper
import com.onurguner.ogauto.input.MirrorAccessibilityService
import com.onurguner.ogauto.input.PhoneKeys
import com.onurguner.ogauto.mirror.PhoneDisplay
import com.onurguner.ogauto.mirror.SurfaceTarget
import com.onurguner.ogauto.setup.StartRequests
import kotlinx.coroutines.launch

class MirrorScreen(carContext: CarContext, private val mediaControls: Boolean = false) : Screen(carContext), DefaultLifecycleObserver {

    private val sink = object : SurfaceSink {
        override fun onAttach(container: SurfaceContainer) {
            val surface = container.surface ?: return
            MirrorGateway.attachSurface(
                SurfaceTarget(surface, container.width, container.height, container.dpi)
            )
            // Car reconnection must not request a capture permission.
            renderStatusIfIdle()
        }

        override fun onDetach() {
            MirrorGateway.detachSurface()
        }

        override fun onTap(x: Float, y: Float) {
            Log.i(TAG, "surface onClick($x, $y)")
            handleTap(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            Log.i(TAG, "surface onScroll($distanceX, $distanceY)")
            handleScroll(distanceX, distanceY)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            handleFling(velocityX, velocityY)
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            handleScale(focusX, focusY, scaleFactor)
        }
    }

    init {
        marker = AppsScreen.MIRROR_MARKER
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        lifecycleScope.launch {
            MirrorGateway.state.collect { renderStatusIfIdle(); invalidate() }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        SurfaceRouter.setSink(sink)
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_menu) { screenManager.pop() })
        if (mediaControls) {
            strip.addAction(action(R.drawable.ic_skip_prev) { PhoneKeys.previous(carContext) })
            strip.addAction(action(R.drawable.ic_play_pause) { PhoneKeys.playPause(carContext) })
            strip.addAction(action(R.drawable.ic_skip_next) { PhoneKeys.next(carContext) })
        } else {
            strip.addAction(action(R.drawable.ic_play_pause) { PhoneKeys.playPause(carContext) })
            strip.addAction(action(R.drawable.ic_back) {
                requireAccessibility { MirrorAccessibilityService.pressBack() }
            })
            strip.addAction(action(R.drawable.ic_home) {
                requireAccessibility { MirrorAccessibilityService.pressHome() }
            })
        }
        // ↻ re-binds the mirror to the car surface: the recovery for a
        // picture that stalled while the phone kept playing.
        val projection = MirrorGateway.state.value.projection
        val reconnect = action(if (projection == ProjectionStatus.ACTIVE) R.drawable.ic_reload else R.drawable.ic_cast) {
            when (MirrorGateway.state.value.projection) {
                ProjectionStatus.ACTIVE -> {
                    MirrorGateway.requestRefresh()
                    CarToast.makeText(carContext, carContext.getString(R.string.car_video_refreshed), CarToast.LENGTH_SHORT).show()
                }
                ProjectionStatus.NONE -> ensureProjection()
                ProjectionStatus.REQUESTED -> CarToast.makeText(carContext, carContext.getString(R.string.car_confirm_on_phone), CarToast.LENGTH_LONG).show()
            }
        }
        val apps = action(R.drawable.ic_grid) { screenManager.push(AppsScreen(carContext)) }
        val recents = action(R.drawable.ic_list) {
            requireAccessibility { MirrorAccessibilityService.pressRecents() }
        }
        return NavigationTemplate.Builder().setActionStrip(strip.build())
            .withPan(apps, recents, reconnect).build()
    }

    private fun ensureProjection() {
        if (MirrorGateway.state.value.projection == ProjectionStatus.NONE) {
            StartRequests.requestCapturePermission(carContext)
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_confirm_on_phone),
                CarToast.LENGTH_LONG,
            ).show()
        }
    }

    private fun renderStatusIfIdle() {
        if (!SurfaceRouter.isActive(sink)) return
        val container = SurfaceRouter.container ?: return
        val state = MirrorGateway.state.value
        if (state.isMirroring) return
        val message = when {
            state.lastError != null ->
                carContext.getString(R.string.car_status_error)
            state.projection == ProjectionStatus.REQUESTED ->
                carContext.getString(R.string.car_status_confirm)
            else ->
                carContext.getString(R.string.car_status_idle)
        }
        StatusRenderer.draw(container, message)
    }

    private fun handleTap(carX: Float, carY: Float) {
        val container = SurfaceRouter.container ?: return
        if (!MirrorGateway.state.value.isMirroring) return
        val (phoneWidth, phoneHeight) = PhoneDisplay.currentSize(carContext)
        val point = TouchMapper.mapCurrent(carX, carY, container.width, container.height, phoneWidth, phoneHeight)
        Log.i(TAG, "tap car=($carX, $carY) phone=${phoneWidth}x$phoneHeight -> $point")
        if (point == null) return
        requireAccessibility {
            val sent = MirrorAccessibilityService.tap(point.x, point.y)
            Log.i(TAG, "dispatchGesture tap sent=$sent")
            sent
        }
    }

    private fun handleScroll(distanceX: Float, distanceY: Float) {
        val container = SurfaceRouter.container ?: return
        if (!MirrorGateway.state.value.isMirroring) return
        val (phoneWidth, phoneHeight) = PhoneDisplay.currentSize(carContext)
        // Scale car-surface distances up to phone pixels, then swipe as a
        // finger would move: end = start - distance, anchored at screen center.
        val scaleX: Float
        val scaleY: Float
        if (MirrorSettings.activeFill) {
            val crop = AspectFit.sourceCrop(
                phoneWidth, phoneHeight, container.width, container.height
            )
            scaleX = crop.width.toFloat() / container.width
            scaleY = crop.height.toFloat() / container.height
        } else {
            val content =
                AspectFit.fit(phoneWidth, phoneHeight, container.width, container.height)
            scaleX = phoneWidth.toFloat() / content.width
            scaleY = phoneHeight.toFloat() / content.height
        }
        val startX = phoneWidth / 2f
        val startY = phoneHeight / 2f
        val endX = (startX - distanceX * scaleX).coerceIn(1f, phoneWidth - 1f)
        val endY = (startY - distanceY * scaleY).coerceIn(1f, phoneHeight - 1f)
        requireAccessibility {
            MirrorAccessibilityService.swipe(startX, startY, endX, endY, durationMs = 100)
        }
    }

    /** A flick on the car screen: a fast swipe on the phone in the same direction. */
    private fun handleFling(velocityX: Float, velocityY: Float) {
        if (!MirrorGateway.state.value.isMirroring) return
        val (phoneWidth, phoneHeight) = PhoneDisplay.currentSize(carContext)
        // ~0.12 s of travel at the fling velocity, kept on screen.
        val dx = (velocityX * 0.12f).coerceIn(-phoneWidth * 0.45f, phoneWidth * 0.45f)
        val dy = (velocityY * 0.12f).coerceIn(-phoneHeight * 0.45f, phoneHeight * 0.45f)
        val startX = phoneWidth / 2f - dx / 2
        val startY = phoneHeight / 2f - dy / 2
        requireAccessibility {
            MirrorAccessibilityService.swipe(startX, startY, startX + dx, startY + dy, durationMs = 60)
        }
    }

    /** Pinch on the car screen (or the zoom buttons): a two-finger pinch on the phone. */
    private fun handleScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        val container = SurfaceRouter.container ?: return
        if (!MirrorGateway.state.value.isMirroring) return
        val (phoneWidth, phoneHeight) = PhoneDisplay.currentSize(carContext)
        // The host reports -1 focus for its own zoom buttons: pinch at center.
        val center = com.onurguner.ogauto.core.PhonePoint(phoneWidth / 2f, phoneHeight / 2f)
        val point = if (focusX < 0 || focusY < 0) center else TouchMapper.mapCurrent(
            focusX, focusY, container.width, container.height, phoneWidth, phoneHeight,
        ) ?: center
        val base = minOf(phoneWidth, phoneHeight) * 0.25f
        val factor = scaleFactor.coerceIn(0.5f, 2f)
        requireAccessibility {
            MirrorAccessibilityService.pinch(
                point.x, point.y, base, base * factor,
                phoneWidth.toFloat(), phoneHeight.toFloat(),
            )
        }
    }

    private fun requireAccessibility(send: () -> Boolean) {
        if (!send()) {
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_accessibility_missing),
                CarToast.LENGTH_LONG,
            ).show()
        }
    }

    private companion object {
        const val TAG = "MirrorScreen"
    }

    private fun action(iconRes: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, iconRes)
                ).build()
            )
            .setOnClickListener(onClick)
            .build()
}
