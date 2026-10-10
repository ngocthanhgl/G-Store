package dev.gstore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetClassificationTest {

    @Test fun `real apk with package mime is eligible`() {
        val e = classifyAsset("app-arm64-v8a.apk", "application/vnd.android.package-archive", 1024)
        assertTrue(e.eligible)
        assertEquals(AssetKind.APK, e.kind)
    }

    @Test fun `apk with null content type is eligible`() {
        assertTrue(classifyAsset("app.apk", null, 1024).eligible)
    }

    @Test fun `apk with octet-stream is eligible`() {
        assertTrue(classifyAsset("app.apk", "application/octet-stream", 1024).eligible)
    }

    @Test fun `exe renamed to apk-sounding name is not eligible`() {
        val e = classifyAsset("app-setup.exe", "application/x-msdownload", 1024)
        assertFalse(e.eligible)
        assertEquals(AssetKind.OTHER, e.kind)
    }

    @Test fun `source zip is archive not apk`() {
        val e = classifyAsset("app-source.zip", "application/zip", 1024)
        assertFalse(e.eligible)
        assertEquals(AssetKind.ARCHIVE, e.kind)
    }

    @Test fun `apks bundle is bundle not directly installable`() {
        val e = classifyAsset("app.apks", "application/octet-stream", 1024)
        assertFalse(e.eligible)
        assertEquals(AssetKind.BUNDLE, e.kind)
    }

    @Test fun `aab is bundle not apk`() {
        assertFalse(classifyAsset("app.aab", "application/octet-stream", 1024).eligible)
    }

    @Test fun `zero-size apk is rejected`() {
        assertFalse(classifyAsset("app.apk", "application/vnd.android.package-archive", 0).eligible)
    }

    @Test fun `extension matching is case-insensitive`() {
        assertTrue(classifyAsset("APP.APK", "application/vnd.android.package-archive", 8).eligible)
    }

    @Test fun `sanitizer strips traversal`() {
        assertEquals("evil.apk", sanitizeFilename("../../evil.apk"))
        assertEquals("evil.apk", sanitizeFilename("C:\\temp\\evil.apk"))
    }

    @Test fun `sanitizer replaces illegal chars and handles blank`() {
        assertEquals("a_b_c.apk", sanitizeFilename("a<b>c.apk"))
        assertEquals("download.apk", sanitizeFilename("   "))
    }

    @Test fun `sanitizer truncates long names`() {
        val long = "a".repeat(200) + ".apk"
        assertTrue(sanitizeFilename(long).length <= 120)
    }
}
