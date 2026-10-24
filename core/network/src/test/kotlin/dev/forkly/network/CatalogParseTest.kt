package dev.forkly.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scanner-catalog JSON parses leniently — unknown keys never break the app. */
class CatalogParseTest {
    private val sample = """
        [{
          "schema": 1,
          "future_field": "ignored",
          "repo": {
            "id": 129710,
            "full_name": "o/app",
            "owner": "o",
            "avatar_url": "https://avatars/x",
            "description": "d",
            "stars": 42,
            "forks": 7,
            "topics": ["android"],
            "archived": false,
            "default_branch": "main",
            "updated_at": "2026-10-09T00:00:00Z",
            "created_at": "2021-01-01T00:00:00Z",
            "license": "MIT"
          },
          "release": {
            "id": 9,
            "tag": "v1.0",
            "name": "1.0",
            "notes": "n",
            "published_at": "2026-10-08T00:00:00Z",
            "html_url": "https://github.com/o/app/releases/tag/v1.0",
            "prerelease": false
          },
          "assets": [{
            "id": 5,
            "name": "app-arm64.apk",
            "content_type": "application/vnd.android.package-archive",
            "size": 123,
            "download_url": "https://github.com/dl",
            "digest": null,
            "download_count": 3
          }]
        }]
    """.trimIndent()

    @Test fun `entry parses with all fields`() {
        val entries = parseCatalog(sample)
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals(129710L, e.repo.id)
        assertEquals("o/app", e.repo.full_name)
        assertEquals(42, e.repo.stars)
        assertEquals(listOf("android"), e.repo.topics)
        assertEquals("v1.0", e.release.tag)
        assertEquals(1, e.assets.size)
        assertNull(e.assets.first().digest) // common: GitHub gives no digest
    }

    @Test fun `minimal entry uses defaults`() {
        val entries = parseCatalog(
            """[{"repo":{"id":1,"full_name":"a/b","owner":"a"},"release":{"id":2,"tag":"t"},"assets":[]}]""",
        )
        val e = entries.first()
        assertEquals("main", e.repo.default_branch)
        assertEquals(0, e.repo.stars)
        assertTrue(e.assets.isEmpty())
    }

    @Test fun `slim index row parses`() {
        val rows = parseIndex(
            """[{"schema":1,"unknown_future":"x","last_verified":"2026-10-11T00:00:00Z","repo":{"id":129710,"full_name":"o/app","owner":"o","stars":42,"forks":7,"archived":false,"updated_at":"2026-10-09T00:00:00Z"},"release":{"id":9,"tag":"v1.0","published_at":"2026-10-08T00:00:00Z","prerelease":true,"apk_count":3}}]""",
        )
        assertEquals(1, rows.size)
        val r = rows.first()
        assertEquals(129710L, r.repo.id)
        assertEquals("o/app", r.repo.full_name)
        assertEquals(42, r.repo.stars)
        assertEquals("v1.0", r.release?.tag)
        assertTrue(r.release?.prerelease == true)
        assertEquals(3, r.release?.apk_count)
    }

    @Test fun `slim index row uses defaults`() {
        val rows = parseIndex("""[{"repo":{"id":1,"full_name":"a/b","owner":"a"},"release":{"id":2,"tag":"t"}}]""")
        val r = rows.first()
        assertEquals(0, r.repo.stars)
        assertEquals(0, r.release?.apk_count)
    }
}
