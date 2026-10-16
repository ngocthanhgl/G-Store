package dev.gstore.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val repoId: Long,
    val fullName: String,
    val addedAt: Long,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT repoId FROM favorites")
    suspend fun idList(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE repoId = :repoId")
    suspend fun delete(repoId: Long)
}

/** Cached repository metadata — single source of truth for catalog UI (flow F). */
@Entity(
    tableName = "repositories",
    indices = [Index("fullName"), Index("description"), Index("owner")],
)
data class RepositoryEntity(
    @PrimaryKey val id: Long,
    val fullName: String,
    val owner: String,
    val avatarUrl: String?,
    val description: String?,
    val stars: Int,
    val forks: Int,
    /** JSON array — topics are display-only, never queried. */
    val topicsJson: String,
    val archived: Boolean,
    val defaultBranch: String,
    val updatedAt: String?,
    /** ISO-8601 creation time, nullable: age unknown for rows synced before v3. */
    val createdAt: String?,
    /** License name/SPDX when reported — never invented. Added in v4. */
    val license: String? = null,
    /** Extended repo facts (v7): populated by full GitHub syncs; null = unknown. */
    val language: String? = null,
    val openIssues: Int? = null,
    val watchers: Int? = null,
    val homepage: String? = null,
    val pushedAt: String? = null,
    val lastSyncedAt: Long,
    /** FRESH | STALE | FAILED:<cause> — see SyncState in core:model. */
    val syncState: String,
)

@Entity(tableName = "releases", indices = [Index("repoId")])
data class ReleaseEntity(
    @PrimaryKey val id: Long,
    val repoId: Long,
    val tag: String,
    val name: String?,
    val notes: String?,
    val publishedAt: String?,
    /** API-provided release page URL. Added in v4. */
    val htmlUrl: String? = null,
    val prerelease: Boolean,
)

@Entity(tableName = "assets", indices = [Index("releaseId")])
data class AssetEntity(
    @PrimaryKey val id: Long,
    val releaseId: Long,
    val filename: String,
    val contentType: String?,
    val size: Long,
    val downloadUrl: String,
    val digest: String?,
    val downloadCount: Long,
    /** Catalog-stage metadata verdict — structural check happens at download (M6). */
    val eligibleApk: Boolean,
)

/** Resumable discovery checkpoint per repo (GITHUB_DISCOVERY §3, flow A). */
@Entity(tableName = "sync_checkpoints")
data class SyncCheckpoint(
    @PrimaryKey val repoFullName: String,
    val nextPage: Int,
    val etag: String?,
    val finishedAt: Long?,
)

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories ORDER BY stars DESC")
    fun observeAll(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun getById(id: Long): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE fullName = :fullName")
    suspend fun getByFullName(fullName: String): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<RepositoryEntity>>

    @Query(
        "SELECT * FROM repositories WHERE " +
            "fullName LIKE '%' || :query || '%' OR " +
            "description LIKE '%' || :query || '%' OR " +
            "owner LIKE '%' || :query || '%' " +
            "ORDER BY stars DESC LIMIT 100",
    )
    fun searchLocal(query: String): Flow<List<RepositoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(repo: RepositoryEntity)
}

@Dao
interface ReleaseDao {
    @Query("SELECT * FROM releases WHERE repoId = :repoId ORDER BY publishedAt DESC")
    fun observeByRepo(repoId: Long): Flow<List<ReleaseEntity>>

    @Query("SELECT * FROM releases WHERE repoId = :repoId")
    suspend fun listByRepo(repoId: Long): List<ReleaseEntity>

    /** Releases with >= 1 eligible APK asset — Famous rule input. */
    @Query(
        "SELECT COUNT(DISTINCT r.id) FROM releases r " +
            "JOIN assets a ON a.releaseId = r.id " +
            "WHERE r.repoId = :repoId AND a.eligibleApk = 1",
    )
    suspend fun countEligibleReleases(repoId: Long): Int

    /**
     * Download volume on recent releases — Trending input.
     * ISO-8601 UTC compares lexicographically == chronologically.
     */
    @Query(
        "SELECT COALESCE(SUM(a.downloadCount), 0) FROM assets a " +
            "JOIN releases r ON r.id = a.releaseId " +
            "WHERE r.repoId = :repoId AND r.publishedAt >= :since",
    )
    suspend fun sumDownloadsSince(repoId: Long, since: String): Long

    /** Earliest eligible-release publish time — New Apps input. */
    @Query(
        "SELECT MIN(r.publishedAt) FROM releases r " +
            "JOIN assets a ON a.releaseId = r.id " +
            "WHERE r.repoId = :repoId AND a.eligibleApk = 1 AND r.publishedAt IS NOT NULL",
    )
    suspend fun firstEligibleReleaseAt(repoId: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(releases: List<ReleaseEntity>)

    @Query("DELETE FROM releases WHERE repoId = :repoId AND id NOT IN (:keepIds)")
    suspend fun pruneExcept(repoId: Long, keepIds: List<Long>)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE releaseId = :releaseId")
    suspend fun listByRelease(releaseId: Long): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE releaseId = :releaseId AND eligibleApk = 1")
    suspend fun listEligibleByRelease(releaseId: Long): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assets: List<AssetEntity>)
}

/** Cached README markdown — bounded, TTL-refreshed (flow B). Added in v4. */
@Entity(tableName = "readmes")
data class ReadmeEntity(
    /** owner/name on the default branch. */
    @PrimaryKey val repoFullName: String,
    val markdown: String,
    val etag: String?,
    val fetchedAt: Long,
)

@Dao
interface ReadmeDao {
    @Query("SELECT * FROM readmes WHERE repoFullName = :fullName")
    fun observe(fullName: String): Flow<ReadmeEntity?>

    @Query("SELECT * FROM readmes WHERE repoFullName = :fullName")
    suspend fun get(fullName: String): ReadmeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(readme: ReadmeEntity)

    @Query("DELETE FROM readmes WHERE repoFullName = :fullName")
    suspend fun clear(fullName: String)

    /** Bounded cache: keep the most recently fetched entries only. */
    @Query(
        "DELETE FROM readmes WHERE repoFullName NOT IN " +
            "(SELECT repoFullName FROM readmes ORDER BY fetchedAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)
}

/** Persistent download queue + history — the single source of truth for flow C. */
@Entity(tableName = "download_records")
data class DownloadRecord(
    /** GitHub asset id — also the unique-work name suffix. */
    @PrimaryKey val assetId: Long,
    val repoFullName: String,
    val releaseTag: String,
    /** Sanitized at enqueue — never a raw server filename. */
    val filename: String,
    val url: String,
    /** Catalog size; -1 when the server never stated one. */
    val expectedSize: Long,
    /** GitHub `digest` ("algo:hex") or null — null means UNAVAILABLE, never failure. */
    val expectedDigest: String?,
    /** QUEUED | ACTIVE | PAUSED | COMPLETED | FAILED | CANCELED. */
    val status: String,
    val bytesDone: Long,
    /** -1 while the server hasn't stated a length. */
    val bytesTotal: Long,
    /** Final local path once COMPLETED. */
    val filePath: String?,
    val error: String?,
    /** VerificationResult name once COMPLETED, else null. */
    val verification: String?,
    /** Manifest facts once structurally validated, else null (never invented). */
    val packageName: String?,
    val versionName: String?,
    /** Manifest versionCode once structurally validated, else null. */
    val versionCode: Long?,
    val minSdk: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_records ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM download_records WHERE assetId = :assetId")
    fun observeById(assetId: Long): Flow<DownloadRecord?>

    @Query("SELECT * FROM download_records WHERE assetId = :assetId")
    suspend fun get(assetId: Long): DownloadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DownloadRecord)

    @Query("DELETE FROM download_records WHERE assetId = :assetId")
    suspend fun delete(assetId: Long)

    @Query("SELECT * FROM download_records WHERE status = :status")
    suspend fun listByStatus(status: String): List<DownloadRecord>

    /** Bounded history: keep only the newest completed/canceled/failed rows. */
    @Query(
        "DELETE FROM download_records WHERE status IN ('COMPLETED','CANCELED','FAILED') " +
            "AND assetId NOT IN (SELECT assetId FROM download_records " +
            "WHERE status IN ('COMPLETED','CANCELED','FAILED') " +
            "ORDER BY updatedAt DESC LIMIT :keep)",
    )
    suspend fun pruneFinished(keep: Int)
}

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM sync_checkpoints WHERE repoFullName = :fullName")
    suspend fun get(fullName: String): SyncCheckpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(checkpoint: SyncCheckpoint)

    @Query("DELETE FROM sync_checkpoints WHERE repoFullName = :fullName")
    suspend fun clear(fullName: String)
}

/** v2 -> v3: nullable creation timestamp; old rows keep age-unknown. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repositories ADD COLUMN createdAt TEXT")
    }
}

/** v3 -> v4: license/htmlUrl columns + README cache table. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repositories ADD COLUMN license TEXT")
        db.execSQL("ALTER TABLE releases ADD COLUMN htmlUrl TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS readmes (" +
                "repoFullName TEXT NOT NULL PRIMARY KEY, " +
                "markdown TEXT NOT NULL, " +
                "etag TEXT, " +
                "fetchedAt INTEGER NOT NULL)",
        )
    }
}

/** v4 -> v5: persistent download queue/history table. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS download_records (" +
                "assetId INTEGER NOT NULL PRIMARY KEY, " +
                "repoFullName TEXT NOT NULL, " +
                "releaseTag TEXT NOT NULL, " +
                "filename TEXT NOT NULL, " +
                "url TEXT NOT NULL, " +
                "expectedSize INTEGER NOT NULL, " +
                "expectedDigest TEXT, " +
                "status TEXT NOT NULL, " +
                "bytesDone INTEGER NOT NULL, " +
                "bytesTotal INTEGER NOT NULL, " +
                "filePath TEXT, " +
                "error TEXT, " +
                "verification TEXT, " +
                "packageName TEXT, " +
                "versionName TEXT, " +
                "minSdk INTEGER, " +
                "createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL)",
        )
    }
}

/** v5 -> v6: manifest versionCode on download records (update checks). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_records ADD COLUMN versionCode INTEGER")
    }
}

/** v6 -> v7: extended repo facts; old rows keep them unknown. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repositories ADD COLUMN language TEXT")
        db.execSQL("ALTER TABLE repositories ADD COLUMN openIssues INTEGER")
        db.execSQL("ALTER TABLE repositories ADD COLUMN watchers INTEGER")
        db.execSQL("ALTER TABLE repositories ADD COLUMN homepage TEXT")
        db.execSQL("ALTER TABLE repositories ADD COLUMN pushedAt TEXT")
    }
}

/** v7 -> v8: indices on the hot query columns (join keys + search LIKE). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_repositories_fullName ON repositories(fullName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_repositories_description ON repositories(description)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_repositories_owner ON repositories(owner)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_releases_repoId ON releases(repoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_assets_releaseId ON assets(releaseId)")
    }
}

@Database(
    entities = [
        FavoriteEntity::class,
        RepositoryEntity::class,
        ReleaseEntity::class,
        AssetEntity::class,
        SyncCheckpoint::class,
        ReadmeEntity::class,
        DownloadRecord::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class GStoreDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun repositories(): RepositoryDao
    abstract fun releases(): ReleaseDao
    abstract fun assets(): AssetDao
    abstract fun checkpoints(): CheckpointDao
    abstract fun readmes(): ReadmeDao
    abstract fun downloads(): DownloadDao
}
