package dev.forkly.details

import dev.forkly.database.DownloadRecord
import dev.forkly.model.ReleaseAsset
import kotlinx.coroutines.flow.Flow

/**
 * The slice of download capability the details hero needs. Implemented in
 * `:app` by delegating to the downloads feature — feature modules never
 * reach into each other (ARCHITECTURE.md).
 */
interface DownloadTracker {
    fun observe(assetId: Long): Flow<DownloadRecord?>
    suspend fun start(asset: ReleaseAsset, repoFullName: String, releaseTag: String): Boolean
    suspend fun cancel(assetId: Long)
}
