package dev.forkly.installation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Platform installation behavior behind an explicit abstraction (flow D).
 * The app never installs silently: callers check [canRequestInstalls], show
 * [InstallerPermissionScreen] when false, open system settings only after
 * explicit user action, re-check on return, then fire the system installer.
 */
interface InstallerGateway {
    fun canRequestInstalls(): Boolean
    fun installIntent(apkFile: File): Intent
    fun settingsIntent(): Intent
    fun packageLabel(): String
}

class SystemInstallerGateway(
    private val context: Context,
    private val authority: String,
) : InstallerGateway {
    private val pm get() = context.packageManager

    override fun canRequestInstalls(): Boolean = pm.canRequestPackageInstalls()

    override fun installIntent(apkFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override fun settingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    override fun packageLabel(): String = context.packageName
}
