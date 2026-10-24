package dev.forkly.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import dev.forkly.database.ForklyDatabase
import dev.forkly.database.MIGRATION_2_3
import dev.forkly.datastore.PreferencesRepository
import dev.forkly.data.CatalogRepository
import dev.forkly.data.ManualRequestGate
import dev.forkly.data.ReadmeRepository
import dev.forkly.data.SignalsCache
import dev.forkly.data.StarSyncEngine
import dev.forkly.database.MIGRATION_3_4
import dev.forkly.database.MIGRATION_4_5
import dev.forkly.database.MIGRATION_5_6
import dev.forkly.database.MIGRATION_6_7
import dev.forkly.database.MIGRATION_7_8
import dev.forkly.database.MIGRATION_8_9
import dev.forkly.datastore.TokenStore
import dev.forkly.downloads.ApkDownloadWorker
import dev.forkly.downloads.DownloadNotifications
import dev.forkly.downloads.DownloadRepository
import dev.forkly.downloads.SystemWorkEnqueuer
import dev.forkly.installation.InstallerGateway
import dev.forkly.installation.SystemInstallerGateway
import dev.forkly.network.CatalogClient
import dev.forkly.network.GitHubApi
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Manual constructor-injection composition root (ponytail: no DI framework
 * until the object graph outgrows this file — see docs/DECISIONS.md).
 */
class ForklyApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WorkManager.initialize(this, workManagerConfiguration)
        // Coil 3 ships no network fetcher in coil-compose: without this,
        // every AsyncImage silently fails and only fallbacks ever show.
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { container.downloadHttp }))
                    add(SvgDecoder.Factory())
                }
                .build()
        }
        // Re-queue downloads interrupted by process death (flow C).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.downloader.reconcile()
        }
        // Restore the GitHub token (if the user logged in) and keep the API
        // client mirroring it across login/logout for the whole process.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.tokens.restore()
            container.tokens.token.collect { container.github.token = it }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                ForklyWorkerFactory(
                    database = container.database,
                    http = container.downloadHttp,
                    apkDir = File(filesDir, "apks"),
                ),
            )
            .build()
}

private class ForklyWorkerFactory(
    private val database: ForklyDatabase,
    private val http: OkHttpClient,
    private val apkDir: File,
) : WorkerFactory() {
    override fun createWorker(
        context: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        ApkDownloadWorker::class.java.name ->
            ApkDownloadWorker(context, workerParameters, database.downloads(), http, apkDir)
        else -> null
    }
}

class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val manualRequestGate = ManualRequestGate()
    val database: ForklyDatabase by lazy {
        // Explicit migrations only — silent data loss is never acceptable (M4).
        Room.databaseBuilder(app, ForklyDatabase::class.java, "forkly.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigration(false)
            .build()
    }
    val preferences: PreferencesRepository by lazy { PreferencesRepository(app) }
    val tokens: TokenStore by lazy { TokenStore(app) }
    val github: GitHubApi by lazy { GitHubApi() }
    val catalog: CatalogRepository by lazy { CatalogRepository.create(github, database) }
    val signals: SignalsCache by lazy { SignalsCache(catalog, appScope) }
    val starSync: StarSyncEngine by lazy { StarSyncEngine(github, catalog, database.starCheckpoints()) }
    /** Daily scanner catalog over the shared download client (flow A). */
    val catalogClient: CatalogClient by lazy { CatalogClient(downloadHttp) }
    val readme: ReadmeRepository by lazy { ReadmeRepository(github, database.readmes()) }
    val downloadHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    val downloader: DownloadRepository by lazy {
        DownloadRepository(database.downloads(), SystemWorkEnqueuer(app), File(app.filesDir, "apks"))
    }
    val downloadNotifications: DownloadNotifications by lazy { DownloadNotifications(app) }
    val installer: InstallerGateway by lazy {
        SystemInstallerGateway(app, "${app.packageName}.apk-provider")
    }
}
