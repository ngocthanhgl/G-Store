package dev.gstore.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.gstore.data.sanitizeFilename
import dev.gstore.database.DownloadDao
import dev.gstore.database.DownloadRecord
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** WorkManager seam — faked in unit tests (WorkManager needs Android). */
interface WorkEnqueuer {
    fun enqueueUnique(name: String, request: androidx.work.OneTimeWorkRequest)
    fun cancelUnique(name: String)
}

class SystemWorkEnqueuer(private val context: Context) : WorkEnqueuer {
    override fun enqueueUnique(name: String, request: androidx.work.OneTimeWorkRequest) {
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelUnique(name: String) {
        WorkManager.getInstance(context).cancelUniqueWork(name)
    }
}

/**
 * Download queue facade (flow C). Room is the single source of truth; the
 * worker only mutates rows, never invents them. Duplicate active downloads
 * for the same asset are refused. Startup [reconcile] re-queues rows the
 * worker never finished (process death) instead of leaving them ACTIVE.
 */
class DownloadRepository(
    private val downloads: DownloadDao,
    private val enqueuer: WorkEnqueuer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAll(): Flow<List<DownloadRecord>> = downloads.observeAll()
    fun observe(assetId: Long): Flow<DownloadRecord?> = downloads.observeById(assetId)

    suspend fun reconcile() {
        val handled = mutableSetOf<Long>()
        for (record in downloads.listByStatus("ACTIVE")) {
            downloads.upsert(record.copy(status = "QUEUED", updatedAt = now()))
            enqueueWork(record)
            handled += record.assetId
        }
        for (record in downloads.listByStatus("QUEUED")) {
            if (record.assetId !in handled) enqueueWork(record)
        }
    }

    /**
     * Enqueues an asset. Returns false when an active download already exists
     * (no duplicates) or the URL is not fetchable.
     */
    suspend fun start(
        assetId: Long,
        repoFullName: String,
        releaseTag: String,
        rawFilename: String,
        url: String,
        expectedSize: Long,
        expectedDigest: String?,
    ): Boolean {
        if (DownloadValidator.checkUrl(url) != null) return false
        val existing = downloads.get(assetId)
        if (existing != null && (existing.status == "QUEUED" || existing.status == "ACTIVE")) {
            return false
        }
        val record = DownloadRecord(
            assetId = assetId,
            repoFullName = repoFullName,
            releaseTag = releaseTag,
            filename = sanitizeFilename(rawFilename),
            url = url,
            expectedSize = expectedSize,
            expectedDigest = expectedDigest,
            status = "QUEUED",
            bytesDone = 0,
            bytesTotal = expectedSize,
            filePath = null,
            error = null,
            verification = null,
            packageName = null,
            versionName = null,
            minSdk = null,
            createdAt = existing?.createdAt ?: now(),
            updatedAt = now(),
        )
        downloads.upsert(record)
        enqueueWork(record)
        return true
    }

    suspend fun cancel(assetId: Long) {
        enqueuer.cancelUnique(ApkDownloadWorker.uniqueName(assetId))
        downloads.get(assetId)?.let {
            if (it.status == "QUEUED" || it.status == "ACTIVE") {
                downloads.upsert(it.copy(status = "CANCELED", updatedAt = now()))
            }
        }
    }

    suspend fun retry(assetId: Long): Boolean {
        val record = downloads.get(assetId) ?: return false
        if (record.status != "FAILED" && record.status != "CANCELED") return false
        val reset = record.copy(
            status = "QUEUED", bytesDone = 0, error = null,
            verification = null, updatedAt = now(),
        )
        downloads.upsert(reset)
        enqueueWork(reset)
        return true
    }

    suspend fun forget(assetId: Long) {
        enqueuer.cancelUnique(ApkDownloadWorker.uniqueName(assetId))
        downloads.delete(assetId)
    }

    fun pruneFinishedAsync(keep: Int = 100) {
        scope.launch { downloads.pruneFinished(keep) }
    }

    private fun enqueueWork(record: DownloadRecord) {
        val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(
                workDataOf(
                    ApkDownloadWorker.KEY_ASSET to record.assetId,
                    ApkDownloadWorker.KEY_URL to record.url,
                    ApkDownloadWorker.KEY_FILENAME to record.filename,
                    ApkDownloadWorker.KEY_EXPECTED_SIZE to record.expectedSize,
                    ApkDownloadWorker.KEY_EXPECTED_DIGEST to record.expectedDigest,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        enqueuer.enqueueUnique(ApkDownloadWorker.uniqueName(record.assetId), request)
    }

    private fun now() = System.currentTimeMillis()
}
