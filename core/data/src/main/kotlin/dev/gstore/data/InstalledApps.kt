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
 * (maintainers rename schemes freely: v1.2.3 vs 1.2.3 vs 1.2.3-stable), so a
 * tag only counts as newer when it parses as a HIGHER version. Anything
 * unknown — null names, unparseable tags — is UP_TO_DATE, never an update.
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
    if (latestTag != null && installedVn != null) {
        val cmp = compareTags(normalizeTag(latestTag), normalizeTag(installedVn))
        if (cmp != null && cmp > 0) return UpdateState.UPDATE_AVAILABLE
    }
    return UpdateState.UP_TO_DATE
}

/** Lowercase, trimmed, leading v/V stripped: "v1.2.3" and "1.2.3" are equal. */
fun normalizeTag(tag: String): String =
    tag.trim().trimStart('v', 'V').lowercase()

/**
 * Segment-wise version compare on [.\-_] boundaries: numeric segments beat
 * lexical ones only by value ("1.10" > "1.9"); non-numeric segments compare
 * lexically; first difference wins. Null when a segment pair is
 * unparseable-mixed (unknown ordering — caller treats as no update).
 */
fun compareTags(a: String, b: String): Int? {
    if (a == b) return 0
    val split = Regex("[.\\-_]")
    val pa = a.split(split)
    val pb = b.split(split)
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val sa = pa.getOrElse(i) { "0" }
        val sb = pb.getOrElse(i) { "0" }
        val na = sa.toLongOrNull()
        val nb = sb.toLongOrNull()
        val cmp = if (na != null && nb != null) na.compareTo(nb)
        else if (na == null && nb == null) sa.compareTo(sb)
        else return null
        if (cmp != 0) return cmp
    }
    return 0
}
