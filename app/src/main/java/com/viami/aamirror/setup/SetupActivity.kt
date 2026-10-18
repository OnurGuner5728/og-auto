package com.onurguner.ogauto.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.onurguner.ogauto.R
import com.onurguner.ogauto.browser.BrowserDisplay
import com.onurguner.ogauto.car.SurfaceRouter
import com.onurguner.ogauto.core.Channel
import com.onurguner.ogauto.core.M3uParser
import com.onurguner.ogauto.video.PlaylistStore
import com.onurguner.ogauto.video.VideoPlayback
import com.onurguner.ogauto.core.UrlResolver
import com.onurguner.ogauto.core.MirrorEvent
import com.onurguner.ogauto.core.MirrorGateway
import com.onurguner.ogauto.core.MirrorState
import com.onurguner.ogauto.core.ProjectionStatus
import com.onurguner.ogauto.input.BrightnessSaver
import com.onurguner.ogauto.input.MirrorAccessibilityService
import com.onurguner.ogauto.input.RotationLock
import com.onurguner.ogauto.mirror.MirrorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SetupActivity : ComponentActivity() {

    private var steps: SetupSteps? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val capture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                MirrorService.start(this, result.resultCode, data)
            } else {
                MirrorGateway.dispatch(MirrorEvent.PermissionDenied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        SettingsStore.load(this)

        findViewById<Button>(R.id.btn_capture).setOnClickListener { requestCapture() }
        steps = SetupSteps(this, findViewById(R.id.steps_container))
        findViewById<Button>(R.id.btn_diag).setOnClickListener { shareDiagnostics() }
        findViewById<Button>(R.id.btn_patreon).setOnClickListener {
            openLink("https://www.patreon.com/c/onurguner")
        }
        findViewById<Button>(R.id.btn_support).setOnClickListener {
            openLink("mailto:onurguner.bjk@gmail.com?subject=OgAuto")
        }
        val playlistField = findViewById<EditText>(R.id.edit_playlist)
        playlistField.setText(PlaylistStore.url(this))
        findViewById<Button>(R.id.btn_playlist).setOnClickListener {
            loadPlaylist(playlistField.text.toString())
        }
        findViewById<Button>(R.id.btn_play_in_car).setOnClickListener { playUrlInCar() }
        findViewById<Button>(R.id.btn_open_url).setOnClickListener { openUrlInCar() }
        findViewById<Button>(R.id.btn_bookmarks).setOnClickListener {
            startActivity(Intent(this, BookmarksActivity::class.java))
        }
        findViewById<EditText>(R.id.edit_url).setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openUrlInCar(); true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            MirrorGateway.state.collect { render(it) }
        }

        handleIntent(intent, initial = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, initial = false)
    }

    private fun handleIntent(intent: Intent, initial: Boolean) {
        when {
            intent.getBooleanExtra(EXTRA_RESTART_CAPTURE, false) -> restartCapture()
            intent.getBooleanExtra(EXTRA_REQUEST_CAPTURE, false) -> requestCapture()
            initial -> showConsent {}
        }
    }

    /**
     * An app-only share froze the car picture: end it and ask again, this
     * time for the whole screen.
     */
    private fun restartCapture() {
        if (MirrorGateway.state.value.projection == ProjectionStatus.ACTIVE) {
            startService(Intent(this, MirrorService::class.java).setAction(MirrorService.ACTION_STOP))
            lifecycleScope.launch {
                withTimeoutOrNull(3000) {
                    MirrorGateway.state.first { it.projection != ProjectionStatus.ACTIVE }
                }
                requestCapture()
            }
        } else {
            requestCapture()
        }
    }

    override fun onResume() {
        super.onResume()
        render(MirrorGateway.state.value)
    }

    private fun loadPlaylist(url: String) {
        val trimmed = url.trim()
        if (!M3uParser.looksLikeUrl(trimmed)) {
            Toast.makeText(this, R.string.video_no_link, Toast.LENGTH_LONG).show()
            return
        }
        PlaylistStore.setUrl(this, trimmed)
        val status = findViewById<TextView>(R.id.txt_playlist)
        status.setText(R.string.video_playlist_loading)
        lifecycleScope.launch {
            status.text = try {
                val list = PlaylistStore.download(this@SetupActivity, trimmed)
                VideoPlayback.setChannels(this@SetupActivity, list)
                getString(R.string.video_playlist_loaded, list.size)
            } catch (e: Exception) {
                getString(R.string.video_playlist_failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** The URL box doubles as "play this stream in the car" for direct video links. */
    private fun playUrlInCar() {
        val text = findViewById<EditText>(R.id.edit_url).text.toString().trim()
        if (!M3uParser.looksLikeUrl(text)) {
            Toast.makeText(this, R.string.video_no_link, Toast.LENGTH_LONG).show()
            return
        }
        VideoPlayback.playSingle(
            this,
            Channel(title = text.substringBefore('?').substringAfterLast('/').ifEmpty { text }, url = text),
        )
        Toast.makeText(
            this,
            if (SurfaceRouter.container != null) getString(R.string.video_sent_to_car, text)
            else getString(R.string.video_open_car),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun openUrlInCar() {
        val query = findViewById<EditText>(R.id.edit_url).text.toString()
        if (query.isBlank()) return
        if (BrowserDisplay.isAttached) {
            BrowserDisplay.loadUrl(UrlResolver.resolve(query))
            Toast.makeText(this, R.string.opened_in_car, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.car_not_connected, Toast.LENGTH_LONG).show()
        }
    }

    private fun showConsent(afterAccept: () -> Unit): Boolean {
        val prefs = getSharedPreferences("consent", MODE_PRIVATE)
        if (!prefs.getBoolean("accepted_v1", false)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("OgAuto — Kullanım onayı / Usage acknowledgement")
                .setMessage("Sürücü sürüş sırasında kullanmamalıdır. Ekranınızdaki bilgiler araçta görünür. Onay bu telefonda saklanır; uygulama verileri silinirse kaldırılır.\n\nThe driver must not use this while driving. Your screen is visible in the car. Consent is stored locally and removed when app data is cleared.")
                .setPositiveButton("Onayla / Accept") { _, _ ->
                    prefs.edit().putBoolean("accepted_v1", true).putLong("time", System.currentTimeMillis()).apply()
                    afterAccept()
                }.setNegativeButton("İptal / Cancel") { _, _ ->
                    MirrorGateway.dispatch(MirrorEvent.PermissionDenied)
                }.setOnCancelListener { MirrorGateway.dispatch(MirrorEvent.PermissionDenied) }.show()
            return false
        }
        return true
    }

    private fun requestCapture() {
        if (MirrorGateway.state.value.projection == ProjectionStatus.ACTIVE) return
        if (!showConsent { requestCapture() }) return
        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Android 14+ offers "share one app" in the consent dialog, and on
        // many phones it is the preselected choice. With it the car only
        // ever sees that one app: open a stream that plays in another task
        // (a player activity, an external player) and the car stays frozen
        // on the last frame of the app that was picked. Asking for the
        // default display removes that option: the whole screen is shared.
        val captureIntent = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        capture.launch(captureIntent)
    }

    private fun render(state: MirrorState) {
        findViewById<TextView>(R.id.txt_status).text = when {
            state.isMirroring -> getString(R.string.status_mirroring)
            state.projection == ProjectionStatus.ACTIVE -> getString(R.string.status_ready)
            state.projection == ProjectionStatus.REQUESTED -> getString(R.string.status_waiting)
            state.lastError != null -> getString(R.string.car_status_error)
            else -> getString(R.string.status_idle)
        }
        steps?.render()
    }

    /** Shares files/diag/mirror.log (mail, WhatsApp, Drive...). */
    private fun shareDiagnostics() {
        val log = com.onurguner.ogauto.mirror.MirrorStats.logFile(this)
        if (!log.isFile || log.length() == 0L) {
            Toast.makeText(this, R.string.diag_empty, Toast.LENGTH_LONG).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.installer", log)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "OgAuto tanı kaydı")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("onurguner.bjk@gmail.com"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(R.string.btn_diag)))
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_REQUEST_CAPTURE = "request_capture"
        const val EXTRA_RESTART_CAPTURE = "restart_capture"
    }
}
