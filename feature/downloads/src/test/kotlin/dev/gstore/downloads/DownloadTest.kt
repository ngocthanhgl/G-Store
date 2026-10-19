package dev.gstore.downloads

import dev.gstore.database.DownloadDao
import dev.gstore.database.DownloadRecord
import dev.gstore.data.UpdateState
import dev.gstore.data.resolveUpdateState
import dev.gstore.model.VerificationResult
import java.io.File
import org.junit.After
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDownloads : DownloadDao {
    private val rows = MutableStateFlow<Map<Long, DownloadRecord>>(emptyMap())
    var enqueued = 0
        private set
    var canceled = 0
        private set

    override fun observeAll(): Flow<List<DownloadRecord>> =
        rows.map { it.values.sortedByDescending { r -> r.updatedAt } }

    override fun observeById(assetId: Long): Flow<DownloadRecord?> =
        rows.map { it[assetId] }

    override suspend fun get(assetId: Long): DownloadRecord? = rows.value[assetId]

    override suspend fun upsert(record: DownloadRecord) {
        rows.value = rows.value + (record.assetId to record)
    }

    override suspend fun delete(assetId: Long) {
        rows.value = rows.value - assetId
    }

    override suspend fun listByStatus(status: String): List<DownloadRecord> =
        rows.value.values.filter { it.status == status }

    override suspend fun pruneFinished(keep: Int) {
        val finished = rows.value.values
            .filter { it.status == "COMPLETED" || it.status == "CANCELED" || it.status == "FAILED" }
            .sortedByDescending { it.updatedAt }
        val drop = finished.drop(keep).map { it.assetId }.toSet()
        rows.value = rows.value - drop
    }

    fun enqueuer() = object : WorkEnqueuer {
        override fun enqueueUnique(name: String, request: androidx.work.OneTimeWorkRequest) {
            enqueued++
        }

        override fun cancelUnique(name: String) {
            canceled++
        }
    }
}

private fun record(assetId: Long = 1, status: String = "QUEUED") = DownloadRecord(
    assetId = assetId, repoFullName = "o/r", releaseTag = "v1",
    filename = "app.apk", url = "https://example.com/app.apk",
    expectedSize = 100, expectedDigest = null, status = status,
    bytesDone = 0, bytesTotal = 100, filePath = null, error = null,
    verification = null, packageName = null, versionName = null, versionCode = null,
    minSdk = null,
    createdAt = 1, updatedAt = 1,
)

class DownloadValidatorTest {
    @Test fun rejectsNonHttps() {
        assertTrue(DownloadValidator.checkUrl("http://example.com/a.apk") != null)
        assertTrue(DownloadValidator.checkUrl("ftp://example.com/a.apk") != null)
        assertTrue(DownloadValidator.checkUrl("https://example.com/a b.apk") != null)
        assertTrue(DownloadValidator.checkUrl("https://example.com/a.apk") == null)
    }

    @Test fun sizeGate() {
        assertTrue(DownloadValidator.checkSize(100, 100) == null)
        assertTrue(DownloadValidator.checkSize(90, 100) != null)
        assertTrue(DownloadValidator.checkSize(0, -1) != null)
        assertTrue(DownloadValidator.checkSize(50, -1) == null)
    }

    @Test fun digestParsing() {
        val parsed = DownloadValidator.parseDigest("sha256:abc123")
        assertEquals("sha256" to "abc123", parsed)
        assertTrue(DownloadValidator.parseDigest(null) == null)
        assertTrue(DownloadValidator.parseDigest("bogus") == null)
        assertTrue(DownloadValidator.parseDigest("sha256:zzzz") == null)
    }

    @Test fun verificationStates() {
        assertEquals(
            VerificationResult.UNAVAILABLE,
            DownloadValidator.verificationFor(null, null),
        )
        assertEquals(
            VerificationResult.CHECKED_OK,
            DownloadValidator.verificationFor("sha256" to "abc", "ABC"),
        )
        assertEquals(
            VerificationResult.MISMATCH,
            DownloadValidator.verificationFor("sha256" to "abc", "def"),
        )
    }
}

class DownloadRepositoryTest {
    private val temporaryFiles = mutableListOf<File>()

    private fun temporaryDirectory(): File =
        java.nio.file.Files.createTempDirectory("gstore-test").toFile().also(temporaryFiles::add)

    @After fun cleanUp() {
        temporaryFiles.forEach { it.deleteRecursively() }
    }

    @Test fun startRefusesDuplicatesAndBadUrls(): Unit = runBlocking {
        val dao = FakeDownloads()
        val repo = DownloadRepository(dao, dao.enqueuer(), temporaryDirectory())
        assertFalse(
            repo.start(1, "o/r", "v1", "a.apk", "http://insecure/x.apk", 10, null),
        )
        assertTrue(
            repo.start(1, "o/r", "v1", "a.apk", "https://example.com/a.apk", 10, null),
        )
        // Second start while QUEUED is refused — no duplicate work.
        assertFalse(
            repo.start(1, "o/r", "v1", "a.apk", "https://example.com/a.apk", 10, null),
        )
        assertEquals(1, dao.enqueued)
    }

    @Test fun cancelAndRetry(): Unit = runBlocking {
        val dao = FakeDownloads()
        val repo = DownloadRepository(dao, dao.enqueuer(), temporaryDirectory())
        repo.start(1, "o/r", "v1", "a.apk", "https://example.com/a.apk", 10, null)
        repo.cancel(1)
        assertEquals("CANCELED", dao.get(1)?.status)
        assertEquals(1, dao.canceled)
        assertTrue(repo.retry(1))
        assertEquals("QUEUED", dao.get(1)?.status)
        assertEquals(2, dao.enqueued)
        // Retry only applies to finished rows.
        assertFalse(repo.retry(1))
    }

    @Test fun reconcileRequeuesInterrupted(): Unit = runBlocking {
        val dao = FakeDownloads()
        dao.upsert(record(status = "ACTIVE"))
        val repo = DownloadRepository(dao, dao.enqueuer(), temporaryDirectory())
        repo.reconcile()
        assertEquals("QUEUED", dao.get(1)?.status)
        assertEquals(1, dao.enqueued)
    }

    @Test fun historyBounded(): Unit = runBlocking {
        val dao = FakeDownloads()
        repeat(5) { dao.upsert(record(assetId = it.toLong(), status = "COMPLETED")) }
        dao.pruneFinished(keep = 2)
        assertEquals(2, dao.listByStatus("COMPLETED").size)
    }

    @Test fun forgetRemovesOnlyTheTrackedApkWithinThePrivateDirectory(): Unit = runBlocking {
        val apkDir = temporaryDirectory()
        val apk = File(apkDir, "app.apk").apply { writeText("apk") }
        val dao = FakeDownloads()
        dao.upsert(record(status = "COMPLETED").copy(filePath = apk.absolutePath))
        val repo = DownloadRepository(dao, dao.enqueuer(), apkDir)

        repo.forget(1)

        assertFalse(apk.exists())
        assertEquals(null, dao.get(1))
        apkDir.deleteRecursively()
    }

    @Test fun forgetRejectsPathsOutsideThePrivateDirectory(): Unit = runBlocking {
        val apkDir = temporaryDirectory()
        val outside = File.createTempFile("gstore-outside", ".apk").also(temporaryFiles::add)
        val dao = FakeDownloads()
        dao.upsert(record(status = "COMPLETED").copy(filePath = outside.absolutePath))
        val repo = DownloadRepository(dao, dao.enqueuer(), apkDir)

        val error = runCatching { repo.forget(1) }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertTrue(outside.exists())
        assertTrue(dao.get(1) != null)
    }
}

class InstalledStateTest {
    @Test fun missingPackageIsNotInstalled() {
        assertEquals(
            UpdateState.NOT_INSTALLED,
            resolveUpdateState(null, 10L, "v2", "v1", null),
        )
    }

    @Test fun olderInstalledThanDownloadedIsReady() {
        assertEquals(
            UpdateState.READY_TO_INSTALL,
            resolveUpdateState(9L, 10L, "v1", "v1", "1.0"),
        )
    }

    @Test fun newerCatalogTagIsUpdateAvailable() {
        assertEquals(
            UpdateState.UPDATE_AVAILABLE,
            resolveUpdateState(10L, 10L, "v2", "v1", "1.0"),
        )
    }

    @Test fun installedMatchingLatestTagIsCurrent() {
        // Sideloaded past our record: installed name already equals latest.
        assertEquals(
            UpdateState.UP_TO_DATE,
            resolveUpdateState(10L, 9L, "v2", "v1", "v2"),
        )
    }

    @Test fun sameEverywhereIsUpToDate() {
        assertEquals(
            UpdateState.UP_TO_DATE,
            resolveUpdateState(10L, 10L, "v1", "v1", "1.0"),
        )
    }

    @Test fun unknownLatestFallsBackToInstalled() {
        assertEquals(
            UpdateState.UP_TO_DATE,
            resolveUpdateState(10L, null, null, "v1", "1.0"),
        )
    }
}
