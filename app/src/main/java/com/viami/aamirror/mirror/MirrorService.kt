package com.onurguner.ogauto.mirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.onurguner.ogauto.R
import com.onurguner.ogauto.core.MirrorEvent
import com.onurguner.ogauto.core.MirrorGateway
import com.onurguner.ogauto.core.MirrorSettings
import com.onurguner.ogauto.input.BrightnessSaver
import com.onurguner.ogauto.input.RotationLock
import com.onurguner.ogauto.input.ScreenKeeper
import kotlinx.coroutines.launch

class MirrorService : LifecycleService() {

    private var projection: MediaProjection? = null
    private var source: LocalScreenSource? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            MirrorGateway.dispatch(MirrorEvent.ProjectionStopped)
            stopSelf()
        }

        // API 34+: the captured content changed size (rotation, fold).
        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.i(TAG, "captured content resized to ${width}x$height")
            source?.onContentResized()
        }

        // API 34+: false means only one app is being shared and it left the
        // screen; the car keeps showing its last frame. Whole-screen sharing
        // is requested up front, this is the fallback for hosts that still
        // hand out an app-only token.
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            Log.i(TAG, "captured content visible=$isVisible")
            if (!isVisible) warnSingleAppCapture()
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.onurguner.ogauto.setup.SettingsStore.load(this)
        lifecycleScope.launch {
            MirrorGateway.surfaceTarget.collect { target ->
                val src = source ?: return@collect
                if (target != null) {
                    src.attach(target)
                    applyLandscape()
                } else {
                    src.detach()
                }
            }
        }
        lifecycleScope.launch {
            MirrorGateway.refresh.collect { source?.refresh() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                acquireProjection(intent)
            }
            ACTION_STOP -> projection?.stop() ?: stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.notification_mirroring))
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireProjection(intent: Intent) {
        if (projection != null) return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null) {
            stopSelf()
            return
        }
        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val acquired = manager.getMediaProjection(resultCode, data) ?: run {
            MirrorGateway.dispatch(MirrorEvent.PermissionDenied)
            stopSelf()
            return
        }
        acquired.registerCallback(projectionCallback, null)
        projection = acquired
        source = LocalScreenSource(this, acquired)
        BrightnessSaver.dim(this)
        ScreenKeeper.start(this)
        applyLandscape()
        MirrorGateway.dispatch(MirrorEvent.ProjectionAcquired)
        // The collector in onCreate saw source == null for the current value;
        // deliver the already-attached surface (if any) by hand.
        MirrorGateway.surfaceTarget.value?.let { source?.attach(it) }
    }

    /** Lock the phone in landscape while sharing, when the user wants it. */
    private fun applyLandscape() {
        if (!MirrorSettings.autoLandscape || RotationLock.isLocked) return
        if (!RotationLock.canLock(this)) {
            Log.i(TAG, "landscape requested but overlay permission missing")
            return
        }
        try {
            RotationLock.lock(this)
        } catch (e: RuntimeException) {
            Log.w(TAG, "landscape lock failed", e)
        }
    }

    private fun warnSingleAppCapture() {
        val manager = getSystemService(NotificationManager::class.java)
        val restart = PendingIntent.getActivity(
            this, 1,
            Intent(this, com.onurguner.ogauto.setup.SetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(com.onurguner.ogauto.setup.SetupActivity.EXTRA_RESTART_CAPTURE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.single_app_title))
            .setContentText(getString(R.string.single_app_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.single_app_text)))
            .setAutoCancel(true)
            .setContentIntent(restart)
            .build()
        manager.notify(WARNING_ID, notification)
    }

    override fun onDestroy() {
        RotationLock.unlock()
        ScreenKeeper.stop()
        BrightnessSaver.restore(this)
        source?.release()
        source = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
        MirrorGateway.dispatch(MirrorEvent.ProjectionStopped)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.onurguner.ogauto.START"
        const val ACTION_STOP = "com.onurguner.ogauto.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "mirror"
        private const val NOTIFICATION_ID = 1
        private const val WARNING_ID = 3
        private const val TAG = "MirrorService"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MirrorService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }
    }
}
