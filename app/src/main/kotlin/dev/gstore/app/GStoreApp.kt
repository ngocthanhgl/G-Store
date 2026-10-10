package dev.gstore.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import dev.gstore.database.GStoreDatabase
import dev.gstore.database.MIGRATION_2_3
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.data.CatalogRepository
import dev.gstore.data.ReadmeRepository
import dev.gstore.database.MIGRATION_3_4
import dev.gstore.database.MIGRATION_4_5
import dev.gstore.downloads.ApkDownloadWorker
import dev.gstore.downloads.DownloadRepository
import dev.gstore.downloads.SystemWorkEnqueuer
import dev.gstore.installation.InstallerGateway
import dev.gstore.installation.SystemInstallerGateway
import dev.gstore.network.CatalogClient
import dev.gstore.network.GitHubApi
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
class GStoreApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WorkManager.initialize(this, workManagerConfiguration)
        // Re-queue downloads interrupted by process death (flow C).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.downloader.reconcile()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                GStoreWorkerFactory(
                    database = container.database,
                    http = container.downloadHttp,
                    apkDir = File(filesDir, "apks"),
                ),
            )
            .build()
}

private class GStoreWorkerFactory(
    private val database: GStoreDatabase,
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

class AppContainer(app: Application) {
    val database: GStoreDatabase by lazy {
        // Explicit migrations only — silent data loss is never acceptable (M4).
        Room.databaseBuilder(app, GStoreDatabase::class.java, "gstore.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration(false)
            .build()
    }
    val preferences: PreferencesRepository by lazy { PreferencesRepository(app) }
    val github: GitHubApi by lazy { GitHubApi() }
    val catalog: CatalogRepository by lazy { CatalogRepository.create(github, database) }
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
        DownloadRepository(database.downloads(), SystemWorkEnqueuer(app))
    }
    val installer: InstallerGateway by lazy {
        SystemInstallerGateway(app, "${app.packageName}.apk-provider")
    }
}
