package dev.forkly.details

import dev.forkly.model.Release
import dev.forkly.database.DownloadRecord
import dev.forkly.network.ContentsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun release(id: Long, tag: String, prerelease: Boolean) =
    Release(id, 1L, tag, null, null, null, null, prerelease)

class DetailsTest {
    @Test fun `auto install only follows a live download completion`() {
        val downloading = HeroDlState.Downloading(50, 100)
        val ready = HeroDlState.ReadyInstall(
            DownloadRecord(
                assetId = 7,
                repoFullName = "o/r",
                releaseTag = "v1",
                filename = "app.apk",
                url = "https://example.com/app.apk",
                expectedSize = 100,
                expectedDigest = null,
                status = "COMPLETED",
                bytesDone = 100,
                bytesTotal = 100,
                filePath = "/tmp/app.apk",
                error = null,
                verification = null,
                packageName = "o.r",
                versionName = "1",
                versionCode = 1,
                minSdk = null,
                createdAt = 1,
                updatedAt = 2,
            ),
        )
        assertEquals(true, shouldAutoInstallHero(downloading, ready))
        assertEquals(false, shouldAutoInstallHero(null, ready))
        assertEquals(false, shouldAutoInstallHero(HeroDlState.IdleDownload, ready))
    }
    @Test fun `latest stable wins by default`() {
        val releases = listOf(release(1L, "v2.0", false), release(2L, "v3.0-beta", true))
        assertEquals(1L, selectDefaultRelease(releases))
    }

    @Test fun `prerelease selected when nothing stable`() {
        assertEquals(7L, selectDefaultRelease(listOf(release(7L, "v1.0-rc1", true))))
    }

    @Test fun `empty releases select nothing`() {
        assertNull(selectDefaultRelease(emptyList()))
    }

    @Test fun `byte formatting uses binary units`() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("12.0 MB", formatBytes(12L * 1024 * 1024))
        assertEquals("1.5 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test fun `repository image picker includes root content and README images only`() {
        val files = listOf(
            ContentsDto("cover.png", "file", 100, "https://raw.githubusercontent.com/o/r/cover.png", null),
            ContentsDto("logo.png", "file", 100, "https://raw.githubusercontent.com/o/r/logo.png", null),
            ContentsDto("photo.jpg", "file", 100, "https://raw.githubusercontent.com/o/r/photo.jpg", null),
            ContentsDto("random.png", "file", 100, "https://raw.githubusercontent.com/o/r/random.png", null),
            ContentsDto("Screenshot_2.png", "file", 100, "https://raw.githubusercontent.com/o/r/shot.png", null),
            ContentsDto("icon.png", "file", 100, "https://raw.githubusercontent.com/o/r/icon.png", null),
        )
        val images = selectRepositoryImages(
            files,
            "![Guide](docs/guide.webp)\n\n![Badge](https://raw.githubusercontent.com/o/r/badge.svg)",
            "o",
            "r",
            "trunk",
        )
        assertEquals(
            listOf("guide.webp", "Screenshot_2.png", "photo.jpg", "random.png"),
            images.map { it.name },
        )
        assertEquals(
            "https://raw.githubusercontent.com/o/r/trunk/docs/guide.webp",
            images.first().url,
        )
    }

    @Test fun `repository image size accepts either dimension at least 500px`() {
        assertEquals(true, meetsMinimumImageSize(500f, 100f))
        assertEquals(true, meetsMinimumImageSize(100f, 500f))
        assertEquals(false, meetsMinimumImageSize(499f, 499f))
    }
}
