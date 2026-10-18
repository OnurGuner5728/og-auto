package com.onurguner.ogauto.setup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.onurguner.ogauto.R
import java.io.File

/**
 * "Install for Android Auto": reinstalls this very APK through the system
 * package installer, asking it to record Google Play as the installing
 * package — what KingInstaller does. Android Auto lists sideloaded car
 * apps more readily when their recorded installer is the Play Store.
 *
 * Nothing here talks to Play or carries a Play signature: the APK keeps its
 * own signing key, and the system installer decides whether to honour the
 * requested installer name. The status line reads the result back.
 */
object InstallerHelper {

    const val PLAY_STORE = "com.android.vending"
    private const val AA_PACKAGE = "com.google.android.projection.gearhead"
    private const val DIR = "installer"
    private const val FILE = "OgAuto.apk"
    private const val TAG = "InstallerHelper"

    /** Package recorded as this app's installer, or null (adb, unknown). */
    fun installer(context: Context): String? = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    } catch (e: Exception) {
        null
    }

    fun isPlayInstalled(context: Context): Boolean = installer(context) == PLAY_STORE

    fun installerLabel(context: Context): String =
        installer(context) ?: context.getString(R.string.install_aa_unknown)

    fun start(activity: Activity) {
        val pm = activity.packageManager
        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.install_aa_allow_unknown, Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                )
            )
            return
        }
        try {
            val apk = copySelf(activity)
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.installer", apk
            )
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, PLAY_STORE)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, false)
            Toast.makeText(activity, R.string.install_aa_started, Toast.LENGTH_LONG).show()
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "self reinstall failed", e)
            Toast.makeText(
                activity,
                activity.getString(R.string.install_aa_failed, e.javaClass.simpleName),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun openAndroidAutoSettings(activity: Activity) {
        try {
            activity.startActivity(Intent("com.google.android.projection.gearhead.SETTINGS"))
        } catch (e: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$AA_PACKAGE"),
                    )
                )
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.aa_not_found, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Copies the installed base APK to files/installer, served by FileProvider. */
    private fun copySelf(context: Context): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory && !dir.mkdirs()) error("cannot create $dir")
        val target = File(dir, FILE)
        val splits = context.applicationInfo.splitSourceDirs
        if (!splits.isNullOrEmpty()) error("split APK install is not supported")
        File(context.applicationInfo.sourceDir).inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        return target
    }
}
