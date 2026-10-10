package dev.gstore.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.gstore.database.DownloadDao
import dev.gstore.installation.ApkParser
import dev.gstore.model.VerificationResult
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resumable APK fetch with validation pipeline (flow C):
 * Range-resume → size check → digest check (when published) → APK structural
 * validation → atomic rename → record update. Recoverable I/O failures return
 * [Result.retry] (bounded by [MAX_ATTEMPTS]); everything else fails fast with
 * a human-readable cause. Never throws past the worker boundary.
 */
class ApkDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val downloads: DownloadDao,
    private val http: OkHttpClient,
    private val apkDir: File,
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_ASSET = "asset_id"
        const val KEY_URL = "url"
        const val KEY_FILENAME = "filename"
        const val KEY_EXPECTED_SIZE = "expected_size"
        const val KEY_EXPECTED_DIGEST = "expected_digest"
        const val PROGRESS_DONE = "bytes_done"
        const val PROGRESS_TOTAL = "bytes_total"
        const val MAX_ATTEMPTS = 4
        const val PROGRESS_BYTES_STEP = 524_288L

        fun uniqueName(assetId: Long) = "apk-$assetId"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val assetId = inputData.getLong(KEY_ASSET, -1)
        val url = inputData.getString(KEY_URL).orEmpty()
        val filename = inputData.getString(KEY_FILENAME).orEmpty()
        val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, -1)
        val expectedDigest = inputData.getString(KEY_EXPECTED_DIGEST)
        val record = downloads.get(assetId)
            ?: return@withContext Result.failure(
                workDataOf("error" to "download record missing for asset $assetId"),
            )
        val urlError = DownloadValidator.checkUrl(url)
        if (urlError != null) {
            return@withContext fail(record, urlError)
        }
        if (!apkDir.exists() && !apkDir.mkdirs()) {
            return@withContext fail(record, "cannot create download directory")
        }
        if (expectedSize > 0 && apkDir.usableSpace < expectedSize) {
            return@withContext fail(record, "insufficient storage")
        }
        markActive(record, 0, expectedSize)
        val part = File(apkDir, "$filename.part")
        val final = File(apkDir, filename)
        try {
            val downloaded = fetchResumable(url, part, expectedSize)
            if (isStopped) {
                update(record.copy(status = "CANCELED", updatedAt = now()))
                return@withContext Result.success()
            }
            val sizeError = DownloadValidator.checkSize(downloaded, expectedSize)
            if (sizeError != null) {
                part.delete()
                return@withContext fail(record, sizeError)
            }
            val published = DownloadValidator.parseDigest(expectedDigest)
            val actualHex = published?.let { (algo, _) -> shaHex(part, algo) }
            val verification = DownloadValidator.verificationFor(published, actualHex)
            if (verification == VerificationResult.MISMATCH) {
                part.delete()
                return@withContext fail(record, "checksum mismatch — file discarded")
            }
            val inspection = ApkParser.inspect(part)
            if (!inspection.structurallyValid) {
                part.delete()
                val cause = inspection.errors.firstOrNull()
                    ?: inspection.crcErrors.firstOrNull()
                    ?: "not a valid APK"
                return@withContext fail(record, cause)
            }
            if (!part.renameTo(final)) {
                return@withContext fail(record, "cannot finalize file")
            }
            val manifest = inspection.manifest
            update(
                record.copy(
                    status = "COMPLETED",
                    bytesDone = downloaded,
                    bytesTotal = if (expectedSize > 0) expectedSize else downloaded,
                    filePath = final.absolutePath,
                    error = null,
                    verification = verification.name,
                    packageName = manifest?.packageName,
                    versionName = manifest?.versionName,
                    minSdk = manifest?.minSdk,
                    updatedAt = now(),
                ),
            )
            downloads.pruneFinished(keep = 100)
            Result.success()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            update(record.copy(status = "CANCELED", updatedAt = now()))
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount + 1 >= MAX_ATTEMPTS || !isRecoverable(e)) {
                part.delete()
                fail(record, e.message ?: e.javaClass.simpleName)
            } else {
                update(record.copy(status = "QUEUED", updatedAt = now()))
                Result.retry()
            }
        }
    }

    private fun isRecoverable(e: Exception): Boolean {
        val name = e.javaClass.name
        return e is java.io.IOException && !name.contains("FileNotFound") &&
            e.message?.contains("refused", ignoreCase = true) != true &&
            e.message?.contains("mismatch", ignoreCase = true) != true &&
            e.message?.contains("storage", ignoreCase = true) != true
    }

    /** GET with Range resume; returns final byte count. */
    private suspend fun fetchResumable(url: String, part: File, expectedSize: Long): Long {
        var offset = if (part.exists()) part.length() else 0L
        // A stale .part larger than a stated size is corrupt — restart.
        if (expectedSize > 0 && offset > expectedSize) {
            part.delete()
            offset = 0
        }
        val request = Request.Builder().url(url).apply {
            if (offset > 0) header("Range", "bytes=$offset-")
            header("Accept", "application/octet-stream")
            header("User-Agent", "GStore/1.0 (Android)")
        }.build()
        http.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val schemeError = DownloadValidator.checkUrl(finalUrl)
            if (schemeError != null) throw java.io.IOException("$schemeError (redirect)")
            if (offset > 0 && response.code != 206) {
                // Server ignored Range — restart from scratch, never append.
                part.delete()
                offset = 0
            } else if (offset == 0L && response.code !in 200..299) {
                throw java.io.IOException("HTTP ${response.code}")
            } else if (response.code !in 200..299 && response.code != 206) {
                throw java.io.IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw java.io.IOException("empty response")
            val total = response.header("Content-Range")
                ?.substringAfter('/', "")
                ?.toLongOrNull()
                ?: response.header("Content-Length")?.toLongOrNull()?.plus(offset)
                ?: expectedSize
            RandomAccessFile(part, "rw").use { out ->
                out.seek(offset)
                val buf = ByteArray(32_768)
                var lastReport = offset
                var lastReportAt = System.currentTimeMillis()
                body.byteStream().use { input ->
                    while (true) {
                        if (isStopped) return offset
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        offset += n
                        val time = System.currentTimeMillis()
                        if (offset - lastReport >= PROGRESS_BYTES_STEP ||
                            time - lastReportAt >= 1_000
                        ) {
                            lastReport = offset
                            lastReportAt = time
                            reportProgress(offset, total)
                            touchProgress(offset, total)
                        }
                    }
                }
            }
            reportProgress(offset, total)
            touchProgress(offset, total)
            return offset
        }
    }

    private fun shaHex(file: File, algo: String): String {
        val md = MessageDigest.getInstance(
            when (algo) {
                "sha1" -> "SHA-1"
                "md5" -> "MD5"
                else -> "SHA-256"
            },
        )
        file.inputStream().use { input ->
            val buf = ByteArray(32_768)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun reportProgress(done: Long, total: Long) {
        setProgress(workDataOf(PROGRESS_DONE to done, PROGRESS_TOTAL to total))
    }

    private suspend fun touchProgress(done: Long, total: Long) {
        val current = downloads.get(inputData.getLong(KEY_ASSET, -1)) ?: return
        if (current.status == "ACTIVE") {
            downloads.upsert(
                current.copy(bytesDone = done, bytesTotal = total, updatedAt = now()),
            )
        }
    }

    private suspend fun markActive(record: dev.gstore.database.DownloadRecord, done: Long, total: Long) {
        downloads.upsert(
            record.copy(status = "ACTIVE", bytesDone = done, bytesTotal = total, error = null, updatedAt = now()),
        )
    }

    private suspend fun update(record: dev.gstore.database.DownloadRecord) {
        downloads.upsert(record.copy(updatedAt = now()))
    }

    private suspend fun fail(record: dev.gstore.database.DownloadRecord, cause: String): Result {
        update(record.copy(status = "FAILED", error = cause))
        return Result.failure(workDataOf("error" to cause))
    }

    private fun now() = System.currentTimeMillis()

    /** Test seam: pure progress-data helper. */
    fun progressData(done: Long, total: Long): Data =
        workDataOf(PROGRESS_DONE to done, PROGRESS_TOTAL to total)
}
