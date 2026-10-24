package dev.forkly.downloads

import dev.forkly.model.VerificationResult

/** Pure, unit-testable gates for flow C — no Android, no I/O. */
object DownloadValidator {
    /** Only unambiguous HTTPS artifact URLs are ever fetched. */
    fun checkUrl(url: String): String? {
        val scheme = url.substringBefore("://", "")
        if (scheme.lowercase() != "https") return "refused non-HTTPS URL"
        if (url.contains('\\') || url.contains(" ")) return "malformed URL"
        return null
    }

    /** Final byte count must match the catalog size when one was stated. */
    fun checkSize(done: Long, expected: Long): String? {
        if (expected > 0 && done != expected) return "size mismatch ($done/$expected)"
        if (done <= 0) return "empty file"
        return null
    }

    /**
     * Parses GitHub's `digest` field ("<algo>:<hex>"). Returns the algorithm
     * and hex, or null when no digest was published (UNAVAILABLE, not failure).
     */
    fun parseDigest(digest: String?): Pair<String, String>? {
        if (digest.isNullOrBlank()) return null
        val algo = digest.substringBefore(':', "").lowercase()
        val hex = digest.substringAfter(':', "")
        if ((algo != "sha256" && algo != "sha1" && algo != "md5") || hex.isEmpty() ||
            hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }
        ) {
            return null
        }
        return algo to hex.lowercase()
    }

    fun verificationFor(
        expected: Pair<String, String>?,
        actualHex: String?,
    ): VerificationResult = when {
        expected == null -> VerificationResult.UNAVAILABLE
        actualHex == null -> VerificationResult.UNAVAILABLE
        actualHex.equals(expected.second, ignoreCase = true) -> VerificationResult.CHECKED_OK
        else -> VerificationResult.MISMATCH
    }
}
