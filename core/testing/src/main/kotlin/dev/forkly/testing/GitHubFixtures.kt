package dev.forkly.testing

import dev.forkly.network.AssetDto
import dev.forkly.network.ReleaseDto
import dev.forkly.network.RepoDto
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; explicitNulls = true }

/**
 * Recorded GitHub payloads (TEST_PLAN §2). No live API in unit tests.
 * Covers: digest present/null, prerelease, draft, misleading names,
 * multi-asset release, bundle/archive kinds.
 */
object GitHubFixtures {
    const val REPO_JSON = """{
      "id": 123,
      "full_name": "example/coolapp",
      "owner": {"login": "example", "avatar_url": "https://avatars.githubusercontent.com/u/1?v=4"},
      "description": "A cool open-source app",
      "stargazers_count": 1500,
      "forks_count": 200,
      "topics": ["android", "music-player"],
      "archived": false,
      "default_branch": "main",
      "updated_at": "2026-09-01T00:00:00Z"
    }"""

    const val RELEASES_PAGE1_JSON = """[
      {
        "id": 1001,
        "tag_name": "v2.0",
        "name": "Version 2.0",
        "body": "Stable release notes",
        "published_at": "2026-08-01T00:00:00Z",
        "prerelease": false,
        "draft": false,
        "assets": [
          {"id": 2001, "name": "coolapp-arm64-v8a.apk", "label": null,
           "content_type": "application/vnd.android.package-archive",
           "size": 12582912, "digest": "sha256:abc123",
           "download_count": 500,
           "browser_download_url": "https://github.com/example/coolapp/releases/download/v2.0/coolapp-arm64-v8a.apk"},
          {"id": 2002, "name": "coolapp-universal.apk", "label": null,
           "content_type": "application/octet-stream",
           "size": 15728640, "digest": null,
           "download_count": 300,
           "browser_download_url": "https://github.com/example/coolapp/releases/download/v2.0/coolapp-universal.apk"},
          {"id": 2003, "name": "coolapp-setup.exe", "label": null,
           "content_type": "application/x-msdownload",
           "size": 8388608, "digest": null,
           "download_count": 10,
           "browser_download_url": "https://github.com/example/coolapp/releases/download/v2.0/coolapp-setup.exe"},
          {"id": 2004, "name": "coolapp-source.zip", "label": null,
           "content_type": "application/zip",
           "size": 4194304, "digest": null,
           "download_count": 5,
           "browser_download_url": "https://github.com/example/coolapp/releases/download/v2.0/coolapp-source.zip"},
          {"id": 2005, "name": "coolapp.apks", "label": null,
           "content_type": "application/octet-stream",
           "size": 14680064, "digest": null,
           "download_count": 50,
           "browser_download_url": "https://github.com/example/coolapp/releases/download/v2.0/coolapp.apks"}
        ]
      },
      {
        "id": 1002,
        "tag_name": "v2.1-beta",
        "name": "Beta",
        "body": "Prerelease notes",
        "published_at": "2026-09-01T00:00:00Z",
        "prerelease": true,
        "draft": false,
        "assets": [
          {"id": 2006, "name": "coolapp-beta.apk", "label": null,
           "content_type": "application/vnd.android.package-archive",
           "size": 12582912, "digest": null,
           "download_count": 20,
           "browser_download_url": "https://github.com/example/coolapp/releases/download/v2.1-beta/coolapp-beta.apk"}
        ]
      },
      {
        "id": 1003,
        "tag_name": "v3.0-draft",
        "name": "Draft",
        "body": null,
        "published_at": null,
        "prerelease": false,
        "draft": true,
        "assets": [
          {"id": 2007, "name": "coolapp-draft.apk", "label": null,
           "content_type": "application/vnd.android.package-archive",
           "size": 12582912, "digest": null,
           "download_count": 0,
           "browser_download_url": "https://github.com/example/coolapp/releases/download/v3.0-draft/coolapp-draft.apk"}
        ]
      }
    ]"""

    const val RELEASES_EMPTY_JSON = "[]"

    fun repo(): RepoDto = json.decodeFromString(RepoDto.serializer(), REPO_JSON)

    fun releasesPage1(): List<ReleaseDto> =
        json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(ReleaseDto.serializer()),
            RELEASES_PAGE1_JSON,
        )

    fun noReleasesRepo(): RepoDto = repo().copy(id = 999, fullName = "example/emptyapp")
}
