package dev.forkly.downloads

import android.content.pm.PackageManager
import android.os.Build
import dev.forkly.data.InstalledAppsGateway
import dev.forkly.data.InstalledInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PackageManager-backed gateway (lives in downloads; contract in core:data). */
class SystemInstalledApps(
    private val pm: PackageManager,
) : InstalledAppsGateway {
    override suspend fun installedPackages(names: Set<String>): Map<String, InstalledInfo> =
        withContext(Dispatchers.IO) {
            names.mapNotNull { name ->
                runCatching {
                    val info = pm.getPackageInfo(name, 0)
                    @Suppress("DEPRECATION")
                    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                    else info.versionCode.toLong()
                    InstalledInfo(
                        packageName = name,
                        label = info.applicationInfo?.let {
                            pm.getApplicationLabel(it).toString()
                        } ?: name,
                        versionName = info.versionName,
                        versionCode = code,
                        lastUpdateTime = info.lastUpdateTime,
                    )
                }.getOrNull()
            }.associateBy { it.packageName }
        }
}
