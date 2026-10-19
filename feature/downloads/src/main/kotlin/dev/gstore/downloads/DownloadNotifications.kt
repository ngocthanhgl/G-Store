package dev.gstore.downloads

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class DownloadNotifications(context: Context) {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    fun progress(assetId: Long, filename: String, done: Long, total: Long) {
        if (!canNotify()) return
        ensureChannel()
        val percent = if (total > 0L) {
            (done.coerceAtLeast(0L).toDouble() / total.toDouble() * 100.0)
                .toInt().coerceIn(0, 100)
        } else {
            0
        }
        val text = if (total > 0L) {
            "${formatBytes(done)} of ${formatBytes(total)}"
        } else {
            "${formatBytes(done)} downloaded"
        }
        notifyDownload(
            assetId,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(filename)
                .setContentText(text)
                .setProgress(100, percent, total <= 0L)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build(),
        )
    }

    fun completed(assetId: Long, filename: String) {
        if (!canNotify()) return
        ensureChannel()
        notifyDownload(
            assetId,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download ready")
                .setContentText(filename)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    fun failed(assetId: Long, filename: String) {
        if (!canNotify()) return
        ensureChannel()
        notifyDownload(
            assetId,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Download failed")
                .setContentText(filename)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build(),
        )
    }

    fun updateAvailable(packageName: String, label: String, releaseTag: String) {
        if (!canNotify()) return
        ensureChannel()
        notify(
            "updates:$packageName",
            packageName.hashCode(),
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Update available")
                .setContentText("$label · $releaseTag")
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    fun clear(assetId: Long) {
        manager.cancel("downloads", assetId.hashCode())
    }

    private fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            appContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Downloads and updates",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "APK download progress and update availability"
                    },
                )
        }
    }

    private fun notifyDownload(assetId: Long, notification: android.app.Notification) {
        notify("downloads", assetId.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun notify(tag: String, id: Int, notification: android.app.Notification) {
        manager.notify(tag, id, notification)
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return "$value B"
        val units = arrayOf("KB", "MB", "GB")
        var number = value.toDouble()
        var index = -1
        do {
            number /= 1024.0
            index++
        } while (number >= 1024.0 && index < units.lastIndex)
        return "%.1f %s".format(number, units[index.coerceAtMost(units.lastIndex)])
    }

    private companion object {
        const val CHANNEL_ID = "downloads_and_updates"
    }
}
