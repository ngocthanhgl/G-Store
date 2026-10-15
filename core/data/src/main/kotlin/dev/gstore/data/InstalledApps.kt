package dev.gstore.data

/**
 * On-device install truth for packages the user fetched through G Store.
 * Only package names we already track (our own download records) are ever
 * queried — never a full device inventory.
 */
data class InstalledInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val lastUpdateTime: Long,
)

interface InstalledAppsGateway {
    /** Subset of [names] currently installed, keyed by package name. */
    suspend fun installedPackages(names: Set<String>): Map<String, InstalledInfo>
}

/** Update verdict for one tracked package. */
enum class UpdateState {
    /** Installed and nothing newer is known. */
    UP_TO_DATE,
    /** A newer file is already downloaded — tap to install it. */
    READY_TO_INSTALL,
    /** The catalog knows a newer release tag than anything installed. */
    UPDATE_AVAILABLE,
    /** A record exists but the package is not on the device. */
    NOT_INSTALLED,
}

/**
 * Pure update verdict. Version codes compare reliably; tags are cosmetic
 * (maintainers rename schemes freely), so a tag mismatch only counts when
 * the installed version name doesn't already equal the latest tag.
 */
fun resolveUpdateState(
    installedVc: Long?,
    downloadedVc: Long?,
    latestTag: String?,
    recordTag: String,
    installedVn: String?,
): UpdateState {
    if (installedVc == null) return UpdateState.NOT_INSTALLED
    if (downloadedVc != null && installedVc < downloadedVc) {
        return UpdateState.READY_TO_INSTALL
    }
    if (latestTag != null && latestTag != recordTag && installedVn != latestTag) {
        return UpdateState.UPDATE_AVAILABLE
    }
    return UpdateState.UP_TO_DATE
}
