package com.onurguner.ogauto.setup

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.onurguner.ogauto.R
import com.onurguner.ogauto.input.MirrorAccessibilityService

/**
 * The numbered setup checklist on the phone: one row per permission or
 * system setting, each with its live state and a button that opens the
 * exact settings page (Play Protect, unknown sources, restricted settings,
 * accessibility, overlay, system settings, battery, notifications, Android
 * Auto developer settings). Rows are rebuilt on every resume.
 */
class SetupSteps(private val activity: Activity, private val container: LinearLayout) {

    private enum class State { DONE, TODO, CHECK }

    private class Step(
        val title: Int,
        val help: Int,
        val button: Int,
        val state: () -> State,
        val open: () -> Unit,
    )

    private val steps: List<Step> = listOf(
        Step(R.string.step_aa_install, R.string.step_aa_install_help, R.string.btn_install_aa,
            { if (InstallerHelper.isPlayInstalled(activity)) State.DONE else State.TODO }) {
            InstallerHelper.start(activity)
        },
        Step(R.string.step_restricted, R.string.step_restricted_help, R.string.step_restricted_btn,
            { if (Build.VERSION.SDK_INT < 33 || MirrorAccessibilityService.enabledBySystem(activity)) State.DONE else State.CHECK }) {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri()))
            Toast.makeText(activity, R.string.step_restricted_toast, Toast.LENGTH_LONG).show()
        },
        Step(R.string.step_accessibility, R.string.step_accessibility_help, R.string.step_open,
            {
                when {
                    MirrorAccessibilityService.ready(activity) -> State.DONE
                    MirrorAccessibilityService.enabledBySystem(activity) -> State.CHECK
                    else -> State.TODO
                }
            }) { openAccessibility() },
        Step(R.string.step_aa_dev, R.string.step_aa_dev_help, R.string.btn_aa_settings,
            { State.CHECK }) { InstallerHelper.openAndroidAutoSettings(activity) },
    )

    fun render() {
        container.removeAllViews()
        val density = activity.resources.displayMetrics.density
        steps.forEachIndexed { i, step ->
            val state = step.state()
            val mark = when (state) {
                State.DONE -> "✓"
                State.TODO -> "✗"
                State.CHECK -> "○"
            }
            val title = TextView(activity).apply {
                text = "$mark  ${i + 1}. ${activity.getString(step.title)}"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(
                    when (state) {
                        State.DONE -> Color.rgb(0x1B, 0x7F, 0x3B)
                        State.TODO -> Color.rgb(0xC6, 0x28, 0x28)
                        State.CHECK -> Color.rgb(0x55, 0x55, 0x55)
                    }
                )
                setPadding(0, (16 * density).toInt(), 0, 0)
            }
            val help = TextView(activity).apply {
                text = if (step.title == R.string.step_accessibility && state == State.CHECK) {
                    activity.getString(R.string.accessibility_enabled_not_bound)
                } else activity.getString(step.help)
                textSize = 13f
            }
            val button = Button(activity).apply {
                text = activity.getString(step.button)
                setOnClickListener { step.open() }
                visibility = if (state == State.DONE) View.GONE else View.VISIBLE
            }
            container.addView(title)
            container.addView(help)
            container.addView(button)
        }
    }

    // ------------------------------------------------------------ helpers

    private fun pkgUri(): Uri = Uri.parse("package:${activity.packageName}")

    private fun open(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun openOrToast(vararg intents: Intent) {
        if (intents.none { open(it) }) {
            Toast.makeText(activity, R.string.step_not_found, Toast.LENGTH_LONG).show()
        }
    }

    private fun openPlayProtect() {
        Toast.makeText(activity, R.string.step_protect_toast, Toast.LENGTH_LONG).show()
        openOrToast(
            Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS"),
            Intent().setClassName(
                "com.google.android.gms",
                "com.google.android.gms.security.settings.VerifyAppsSettingsActivity",
            ),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
    }

    private val serviceComponent: ComponentName
        get() = ComponentName(activity, MirrorAccessibilityService::class.java)

    private fun isXiaomiFamily(): Boolean = Build.MANUFACTURER.lowercase() in
        setOf("xiaomi", "redmi", "poco")

    private fun openAutoStartSettings() {
        // The user, not OgAuto, controls these MIUI/HyperOS settings.
        openOrToast(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri()),
        )
    }

    private fun openAccessibility() {
        // Straight to OgAuto's switch where the platform supports it.
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent.flattenToString())
        openOrToast(details, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun batteryUnrestricted(): Boolean =
        activity.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(activity.packageName)

    @android.annotation.SuppressLint("BatteryLife")
    private fun openBattery() {
        openOrToast(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri()),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun openPhoneDeveloperOptions() {
        val enabled = Settings.Global.getInt(
            activity.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0,
        ) == 1
        if (enabled) {
            openOrToast(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } else {
            Toast.makeText(activity, R.string.step_phone_dev_toast, Toast.LENGTH_LONG).show()
            openOrToast(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }
}
