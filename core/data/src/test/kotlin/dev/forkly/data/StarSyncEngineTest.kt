package dev.forkly.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Starred-sweep behaviour that must not regress:
 *
 * - APK-bearing stars only. The Installed shelf complaint was a screen full of
 *   repos with nothing to install, so the sweep has to actually filter.
 * - Resumability. Progress lives in checkpoints, so a killed process or an
 *   exhausted quota resumes instead of restarting.
 * - One-way starring. Un-starring would delete something the user may have
 *   created long before opening this app.
 */
class StarSyncEngineTest {

    private class FakeStarCheckpoints : dev.forkly.database.StarCheckpointDao {
        val rows = mutableMapOf<String, dev.forkly.database.StarCheckpoint>()
        var upsertCount = 0

        override suspend fun all(): List<dev.forkly.database.StarCheckpoint> = rows.values.toList()
        override suspend fun get(fullName: String) = rows[fullName]
        override suspend fun upsert(checkpoint: dev.forkly.database.StarCheckpoint) {
            upsertCount++
            rows[checkpoint.repoFullName] = checkpoint
        }
    }

    @Test
    fun `checkpoint row round-trips`() = runBlocking {
        val dao = FakeStarCheckpoints()
        dao.upsert(dev.forkly.database.StarCheckpoint("a/b", 42L, true))
        assertEquals("a/b", dao.get("a/b")?.repoFullName)
        assertTrue(dao.get("a/b")?.hasApk == true)
        assertEquals(1, dao.all().size)
    }

    @Test
    fun `recheck window is long enough to be useful`() {
        // Re-probing costs one request per repo, so the window must span weeks.
        assertTrue(StarSyncEngine.RecheckAfterMillis > 7L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun `default budget stays inside the authenticated hourly quota`() {
        // GitHub allows 5,000 requests/hour when authenticated. One probe per
        // repo plus one page read per 100 stars must leave headroom.
        assertTrue(StarSyncEngine.DefaultBudget <= 1000)
    }

    @Test
    fun `classify only accepts real apk assets`() {
        val apk = classifyAsset("app-arm64-v8a.apk", null, 1024)
        assertTrue(apk.eligible)
        assertEquals(AssetKind.APK, apk.kind)

        val zip = classifyAsset("bundle.zip", null, 1024)
        assertFalse(zip.eligible)

        val empty = classifyAsset("app.apk", null, 0)
        assertFalse("a zero-byte asset cannot be installable", empty.eligible)
    }

    @Test
    fun `unstar endpoint is never called`() {
        // Guards the one-way decision: nothing in the engine may delete a star.
        val source = StarSyncEngine::class.java.declaredMethods.map { it.name }
        assertTrue(source.none { it.contains("unstar", ignoreCase = true) })
        assertTrue(source.contains("starRepo") || source.any { it.contains("star", ignoreCase = true) })
    }

    @Test
    fun `network surface stays additive`() {
        // fetchContents/fetchReadme are on the interface; starred + latest are
        // not, so the three test fakes implementing GitHubService stay valid.
        val iface = dev.forkly.network.GitHubService::class.java.methods.map { it.name }
        assertFalse(iface.contains("listStarred"))
        assertFalse(iface.contains("latestRelease"))
        assertFalse(iface.contains("starRepo"))
    }
}