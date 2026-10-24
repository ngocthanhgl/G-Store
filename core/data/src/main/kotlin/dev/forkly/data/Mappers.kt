package dev.forkly.data

import dev.forkly.database.AssetEntity
import dev.forkly.database.ReleaseEntity
import dev.forkly.database.RepositoryEntity
import dev.forkly.model.Release
import dev.forkly.model.ReleaseAsset
import dev.forkly.model.Repository
import dev.forkly.network.AssetDto
import dev.forkly.network.ReleaseDto
import dev.forkly.network.RepoDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun RepoDto.toEntity(state: String, syncedAt: Long): RepositoryEntity = RepositoryEntity(
    id = id,
    fullName = fullName,
    owner = owner.login,
    avatarUrl = owner.avatarUrl,
    description = description,
    stars = stars,
    forks = forks,
    topicsJson = json.encodeToString(ListSerializer(String.serializer()), topics),
    archived = archived,
    defaultBranch = defaultBranch,
    updatedAt = updatedAt,
    createdAt = createdAt,
    // SPDX id when GitHub knows one ("MIT", "NOASSERTION"); else the license name.
    license = license?.spdxId?.takeUnless { it == "NOASSERTION" } ?: license?.name,
    language = language,
    openIssues = openIssues,
    watchers = watchers,
    homepage = homepage,
    pushedAt = pushedAt,
    lastSyncedAt = syncedAt,
    syncState = state,
)

fun ReleaseDto.toEntity(repoId: Long): ReleaseEntity = ReleaseEntity(
    id = id,
    repoId = repoId,
    tag = tag,
    name = name,
    notes = notes,
    publishedAt = publishedAt,
    htmlUrl = htmlUrl,
    prerelease = prerelease,
)

fun AssetDto.toEntity(releaseId: Long): AssetEntity {
    val verdict = classifyAsset(name, contentType, size)
    return AssetEntity(
        id = id,
        releaseId = releaseId,
        filename = name,
        contentType = contentType,
        size = size,
        downloadUrl = downloadUrl,
        digest = digest,
        downloadCount = downloadCount,
        eligibleApk = verdict.eligible,
    )
}

fun RepositoryEntity.toDomain(): Repository = Repository(
    id = id,
    fullName = fullName,
    owner = owner,
    avatarUrl = avatarUrl,
    description = description,
    stars = stars,
    forks = forks,
    topics = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), topicsJson)
    }.getOrDefault(emptyList()),
    archived = archived,
    defaultBranch = defaultBranch,
    updatedAt = updatedAt,
    createdAt = createdAt,
    license = license,
    language = language,
    openIssues = openIssues,
    watchers = watchers,
    homepage = homepage,
    pushedAt = pushedAt,
    catalogVisible = syncState != "SEARCH_ONLY",
)

fun ReleaseEntity.toDomain(): Release = Release(
    id = id,
    repoId = repoId,
    tag = tag,
    name = name,
    notes = notes,
    publishedAt = publishedAt,
    htmlUrl = htmlUrl,
    prerelease = prerelease,
)

fun AssetEntity.toDomain(): ReleaseAsset = ReleaseAsset(
    id = id,
    releaseId = releaseId,
    filename = filename,
    contentType = contentType,
    size = size,
    downloadUrl = downloadUrl,
    digest = digest,
    downloadCount = downloadCount,
)
