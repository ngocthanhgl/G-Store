package dev.forkly.data

import dev.forkly.model.AbiConfidence
import dev.forkly.model.AbiType
import dev.forkly.model.ApkArtifact

/** Catalog-stage verdict. BUNDLE/ARCHIVE get their own label, never "installable". */
enum class AssetKind { APK, BUNDLE, ARCHIVE, OTHER }

data class Eligibility(val eligible: Boolean, val kind: AssetKind)

/**
 * Metadata-only filter (GITHUB_DISCOVERY §4). Structural APK validation happens
 * at download time (M6) — a `.apk` name alone never proves installability, and
 * a strange content-type alone never disproves it: GitHub serves the same APK
 * under tooling-dependent types (x-zip-compressed, java-archive, ...), so any
 * non-empty `.apk` is eligible and the structural check is the real gate.
 */
fun classifyAsset(filename: String, contentType: String?, size: Long): Eligibility {
    if (size <= 0L) return Eligibility(false, AssetKind.OTHER)
    val lower = filename.lowercase()
    val ext = lower.substringAfterLast('.', "")
    return when (ext) {
        "apk" -> Eligibility(true, AssetKind.APK)
        "apks", "xapk", "aab" -> Eligibility(false, AssetKind.BUNDLE)
        "zip", "tar", "gz", "tgz", "rar", "7z", "jar" -> Eligibility(false, AssetKind.ARCHIVE)
        else -> Eligibility(false, AssetKind.OTHER)
    }
}

/**
 * Strips path traversal and illegal characters. Never trusted as proof of type —
 * only as a safe local name (SECURITY_MODEL §2).
 */
fun sanitizeFilename(name: String): String {
    var base = name.substringAfterLast('/').substringAfterLast('\\').trim()
    base = base.replace(Regex("[<>:\"|?*\\x00-\\x1F]"), "_")
    while (base.contains("..")) base = base.replace("..", "_")
    if (base.isBlank()) return "download.apk"
    if (base.length > 120) {
        val ext = base.substringAfterLast('.', "")
        val stem = base.dropLast(if (ext == base) 0 else ext.length + 1)
        base = stem.take(120 - ext.length - 1) + if (ext == base) "" else ".$ext"
    }
    return base
}

data class AbiVerdict(val abi: AbiType, val confidence: AbiConfidence)

/**
 * Filename/label ABI inference — MEDIUM at best (DATA_MODEL §AbiType).
 * Order matters: 64-bit names contain 32-bit substrings. Multiple distinct
 * ABI hits mean an ambiguous name, not a universal build — reported UNKNOWN.
 * No match on an APK means unknown (possibly no native libs), never "universal".
 */
fun detectAbi(filename: String, label: String?): AbiVerdict {
    val text = "${label.orEmpty()} $filename".lowercase()
    val hits = mutableSetOf<AbiType>()
    if ("arm64-v8a" in text || "aarch64" in text || "arm64" in text) hits += AbiType.ARM64_V8A
    if ("armeabi-v7a" in text || "armeabi" in text || "armv7" in text || "arm32" in text) {
        hits += AbiType.ARMEABI_V7A
    }
    if ("x86_64" in text || "x64" in text || "amd64" in text) hits += AbiType.X86_64
    // Bare "x86" only when no 64-bit marker matched (x86_64 contains x86).
    if (AbiType.X86_64 !in hits && "x86" in text) hits += AbiType.X86
    if (hits.size > 1) return AbiVerdict(AbiType.UNKNOWN, AbiConfidence.LOW)
    if (hits.size == 1) return AbiVerdict(hits.single(), AbiConfidence.MEDIUM)
    if ("universal" in text || "-all." in text || "_all." in text) {
        return AbiVerdict(AbiType.UNIVERSAL, AbiConfidence.MEDIUM)
    }
    // "noarch" is a packaging claim, not a verified ABI — never trusted.
    return AbiVerdict(AbiType.UNKNOWN, AbiConfidence.UNKNOWN)
}

/** Catalog-stage artifact card — minSdk/versionName need manifest parsing (M6). */
fun artifactOf(
    assetId: Long,
    filename: String,
    label: String?,
    kind: AssetKind,
): ApkArtifact {
    val verdict = detectAbi(filename, label)
    val lower = filename.lowercase()
    return ApkArtifact(
        assetId = assetId,
        abi = if (kind == AssetKind.APK) verdict.abi else AbiType.UNKNOWN,
        confidence = if (kind == AssetKind.APK) verdict.confidence else AbiConfidence.UNKNOWN,
        minSdk = null,
        versionName = null,
        isSplitBundle = lower.endsWith(".apks") || lower.endsWith(".xapk"),
    )
}
