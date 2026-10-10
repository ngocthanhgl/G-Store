package dev.gstore.installation

import android.os.Build
import dev.gstore.model.AbiConfidence
import dev.gstore.model.AbiType
import dev.gstore.model.AndroidCompatibility
import dev.gstore.model.DeviceSupport
import java.io.File
import java.util.zip.ZipFile

/**
 * APK structural inspection (flow C/D). Uses `java.util.zip` (lazy per-entry
 * reads + CRC verification on full stream reads) — no new dependency.
 *
 * Bounds (SECURITY_MODEL §2): entry count, total uncompressed size, and the
 * manifest bytes we pull into memory are all capped so a hostile file can only
 * produce an [ApkInspection] full of errors, never an OOM.
 */
object ApkParser {
    const val MAX_ENTRIES = 20_000
    const val MAX_UNCOMPRESSED_BYTES = 1_000_000_000L
    const val MAX_MANIFEST_BYTES = 4_000_000
    const val MANIFEST_NAME = "AndroidManifest.xml"

    data class ManifestInfo(
        val packageName: String?,
        val versionCode: Long?,
        val versionName: String?,
        val minSdk: Int?,
    )

    data class ApkInspection(
        /** False when the file is not a readable ZIP at all. */
        val isZip: Boolean,
        val hasManifest: Boolean,
        val hasDex: Boolean,
        /** Manifest attributes — nulls mean "unparseable/unknown", never invented. */
        val manifest: ManifestInfo?,
        /** ABIs from `lib/<abi>/` entries — empty means "no native libs" (pure-Java OK). */
        val abis: Set<AbiType>,
        /** Entry names that escape the archive root (`..` / absolute). */
        val traversalEntries: List<String>,
        /** CRC mismatches found while streaming entry contents. */
        val crcErrors: List<String>,
        val errors: List<String>,
    ) {
        val structurallyValid: Boolean
            get() = isZip && hasManifest && traversalEntries.isEmpty() &&
                crcErrors.isEmpty() && errors.isEmpty()
    }

    fun inspect(file: File): ApkInspection {
        val errors = mutableListOf<String>()
        val traversal = mutableListOf<String>()
        val crcErrors = mutableListOf<String>()
        val abis = mutableSetOf<AbiType>()
        var manifest: ManifestInfo? = null
        var hasManifest = false
        var hasDex = false
        val zip: ZipFile = try {
            ZipFile(file)
        } catch (e: Exception) {
            return ApkInspection(
                isZip = false, hasManifest = false, hasDex = false,
                manifest = null, abis = emptySet(), traversalEntries = emptyList(),
                crcErrors = emptyList(), errors = listOf("not a readable ZIP: ${e.message}"),
            )
        }
        zip.use {
            val entries = it.entries().asSequence().toList()
            if (entries.size > MAX_ENTRIES) {
                errors += "too many entries (${entries.size})"
                return ApkInspection(
                    true, false, false, null, emptySet(),
                    emptyList(), emptyList(), errors,
                )
            }
            var total = 0L
            for (entry in entries) {
                val size = entry.size.coerceAtLeast(0)
                total += size
                if (total > MAX_UNCOMPRESSED_BYTES) {
                    errors += "uncompressed size exceeds 1 GB bound"
                    break
                }
                val name = entry.name
                if (name.startsWith("/") || name.contains("..")) traversal += name
                if (name == MANIFEST_NAME) hasManifest = true
                if (name == "classes.dex" || (name.startsWith("classes") && name.endsWith(".dex"))) {
                    hasDex = true
                }
                abiFromLibPath(name)?.let { abi -> abis += abi }
            }
            if (!hasManifest) errors += "AndroidManifest.xml missing"
            // CRCs are verified manually: ZipFile does not reliably check STORED
            // entry CRCs on stream reads, so stream every entry, hash the
            // uncompressed bytes, and compare against the central directory.
            for (entry in entries) {
                if (entry.isDirectory || entry.size <= 0) continue
                try {
                    val checker = java.util.zip.CRC32()
                    var read = 0L
                    it.getInputStream(entry).use { stream ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            checker.update(buf, 0, n)
                            read += n
                        }
                    }
                    if (entry.crc != -1L && checker.value != entry.crc) {
                        crcErrors += "${entry.name}: CRC mismatch"
                    }
                    if (entry.size != -1L && read != entry.size) {
                        crcErrors += "${entry.name}: truncated ($read/${entry.size})"
                    }
                } catch (e: Exception) {
                    crcErrors += "${entry.name}: ${e.message}"
                }
            }
            if (hasManifest) {
                manifest = try {
                    val entry = it.getEntry(MANIFEST_NAME)
                    val size = entry?.size ?: 0
                    if (entry == null || size <= 0 || size > MAX_MANIFEST_BYTES) {
                        errors += "manifest unreadable (size=$size)"
                        null
                    } else {
                        val bytes = it.getInputStream(entry).readBytes()
                        AxmlParser.parse(bytes)
                    }
                } catch (e: Exception) {
                    errors += "manifest parse failed: ${e.message}"
                    null
                }
            }
        }
        return ApkInspection(
            isZip = true, hasManifest = hasManifest, hasDex = hasDex,
            manifest = manifest, abis = abis, traversalEntries = traversal,
            crcErrors = crcErrors, errors = errors,
        )
    }

    private fun abiFromLibPath(name: String): AbiType? {
        if (!name.startsWith("lib/")) return null
        val abiDir = name.removePrefix("lib/").substringBefore('/', "")
        return when (abiDir) {
            "arm64-v8a" -> AbiType.ARM64_V8A
            "armeabi-v7a" -> AbiType.ARMEABI_V7A
            "x86" -> AbiType.X86
            "x86_64" -> AbiType.X86_64
            else -> null
        }
    }

    /** Manifest-derived ABI set upgrades filename inference to HIGH confidence. */
    fun confidenceFor(abis: Set<AbiType>, filenameVerdict: AbiConfidence): AbiConfidence =
        if (abis.isNotEmpty()) AbiConfidence.HIGH else filenameVerdict

    /** Device support from manifest contents + platform APIs (AGENTS §9). */
    fun deviceSupport(minSdk: Int?, abis: Set<AbiType>): AndroidCompatibility {
        // Static-field reads (never method calls) stay unit-test safe: SDK_INT is 0
        // on the JVM, SUPPORTED_ABIS is null there — both handled explicitly.
        val sdkInt = try { Build.VERSION.SDK_INT } catch (e: Exception) { 0 }
        if (minSdk != null && sdkInt > 0 && sdkInt < minSdk) {
            return AndroidCompatibility(minSdk, DeviceSupport.NO)
        }
        if (abis.isEmpty()) return AndroidCompatibility(minSdk, DeviceSupport.UNKNOWN)
        val supportedAbis = try { Build.SUPPORTED_ABIS?.toList() ?: emptyList() } catch (e: Exception) {
            emptyList()
        }
        val device = supportedAbis.mapNotNull {
            when (it) {
                "arm64-v8a" -> AbiType.ARM64_V8A
                "armeabi-v7a" -> AbiType.ARMEABI_V7A
                "x86" -> AbiType.X86
                "x86_64" -> AbiType.X86_64
                else -> null
            }
        }.toSet()
        val supported = abis.any { it in device || it == AbiType.UNIVERSAL }
        return AndroidCompatibility(minSdk, if (supported) DeviceSupport.YES else DeviceSupport.NO)
    }
}
