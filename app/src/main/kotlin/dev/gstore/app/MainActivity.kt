package dev.gstore.app

import dev.gstore.ui.R as UiRes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.data.AppCategory
import dev.gstore.data.CatalogRepository
import dev.gstore.data.categoryOf
import dev.gstore.database.DownloadRecord
import dev.gstore.designsystem.GStoreTheme
import dev.gstore.downloads.DownloadsScreen
import dev.gstore.downloads.DownloadsViewModel
import dev.gstore.downloads.SystemInstalledApps
import dev.gstore.details.DetailsScreen
import dev.gstore.details.DetailsViewModel
import dev.gstore.details.DownloadTracker
import dev.gstore.home.CategoryFullScreen
import dev.gstore.home.HomeScreen
import dev.gstore.home.HomeViewModel
import dev.gstore.installation.InstallerPermissionScreen
import dev.gstore.readme.ReadmeScreen
import dev.gstore.readme.ReadmeViewModel
import dev.gstore.model.Repository
import dev.gstore.search.SearchScreen
import dev.gstore.search.SearchViewModel
import dev.gstore.settings.SettingsScreen
import dev.gstore.settings.SettingsViewModel
import dev.gstore.ui.EmptyContent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object Home
@Serializable data object Search
@Serializable data object Downloads
@Serializable data object Settings
@Serializable data class Details(val repoId: Long, val fullName: String = "")
@Serializable data class CategoryFull(val name: String)
@Serializable data class Readme(val owner: String, val name: String)

private data class TopDestination(
    val route: Any,
    val label: String,
    /** Drawable res id: static lists cannot hold @Composable Painters. */
    val icon: Int,
)

private val DESTINATIONS = listOf(
    TopDestination(Home, "Home", UiRes.drawable.ic_fluent_home_24_regular),
    TopDestination(Search, "Search", UiRes.drawable.ic_fluent_search_24_regular),
    TopDestination(Downloads, "Installed", UiRes.drawable.ic_fluent_arrow_download_24_regular),
    TopDestination(Settings, "Settings", UiRes.drawable.ic_fluent_settings_24_regular),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: content draws behind the status bar, display cutout,
        // and navigation bar; screens pad with safe-drawing insets below.
        enableEdgeToEdge()
        // Cutout mode exists only on API 28+; older devices letterbox by default.
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val container = (application as GStoreApp).container
        setContent {
            // Restore persisted appearance before first frame (flow E).
            val appearance by container.preferences.appearance.collectAsState(
                initial = AppearanceSettings(),
            )
            val darkTheme = when (appearance.theme) {
                dev.gstore.datastore.ThemeMode.LIGHT -> false
                dev.gstore.datastore.ThemeMode.DARK -> true
                dev.gstore.datastore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                dev.gstore.datastore.ThemeMode.AMOLED -> true
            }
            GStoreTheme(settings = appearance) {
                SystemBarContrast(darkTheme)
                GStoreShell(container)
            }
        }
    }
}

/**
 * Status/navigation icon contrast follows the APP theme (not the system
 * one): dark icons on light scrims, light icons on dark scrims. Bars stay
 * transparent so content shows through edge-to-edge.
 */
@Composable
private fun SystemBarContrast(darkTheme: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

/** Minimal constructor-injection ViewModel factory (no DI framework — DECISIONS.md). */
inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/**
 * Compact → bottom bar; expanded (sw>=600dp, incl. API-37 forced resize) → rail.
 * Never assumes orientation.
 */
@Composable
private fun GStoreShell(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val onOpenRepo: (Repository) -> Unit = { nav.navigate(Details(it.id, it.fullName)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        if (expanded) {
                Row(Modifier.fillMaxSize()) {
                Rail(
                    current = entry?.destination,
                    navigate = { navigateTopLevel(nav, it) },
                    )
                // Screens own their top inset (statusBarsPadding) — except
                // Details, whose hero bleeds under the status bar.
                Box(
                    Modifier
                        .weight(1f)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                        ),
                ) {
                    Content(
                        nav = nav,
                        container = container,
                        snackbar = snackbar,
                        onOpenRepo = onOpenRepo,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Scaffold(
                // Screens pad their own top (statusBarsPadding); the bottom
                // bar owns the navigation-bar area and pads itself.
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal,
                ),
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.height(64.dp),
                        ) {
                            DESTINATIONS.forEach { d ->
                                val selected = entry?.destination?.hasRoute(d.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navigateTopLevel(nav, d.route)
                                    },
                                    icon = {
                                        Icon(
                                            painterResource(d.icon),
                                            contentDescription = d.label,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    },
                                    label = { Text(d.label) },
                                    alwaysShowLabel = false,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    Content(
                        nav = nav,
                        container = container,
                        snackbar = snackbar,
                        onOpenRepo = onOpenRepo,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun androidx.navigation.NavOptionsBuilder.popToRoot(
    nav: androidx.navigation.NavHostController,
) {
    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * Top-level tab navigation with a same-tab guard and a hard-reset
 * fallback. The same-tab tap is a no-op (no reload flash); if the
 * back stack does not land on the target — observed once as "tap Home
 * from Details stays put" — everything above the start destination is
 * popped and the target is navigated to plainly.
 */
private fun navigateTopLevel(nav: androidx.navigation.NavHostController, route: Any) {
    val target = route::class
    if (nav.currentDestination?.hasRoute(target) == true) return
    nav.navigate(route) { popToRoot(nav) }
    if (nav.currentDestination?.hasRoute(target) != true) {
        nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
        nav.navigate(route) { launchSingleTop = true }
    }
}

@Composable
private fun Rail(
    current: androidx.navigation.NavDestination?,
    navigate: (Any) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            dev.gstore.ui.AppIcon(
                avatarUrl = null,
                owner = "G Store",
                size = 40.dp,
            )
        },
    ) {
        DESTINATIONS.forEach { d ->
            NavigationRailItem(
                selected = current?.hasRoute(d.route::class) == true,
                onClick = { navigate(d.route) },
                icon = { Icon(painterResource(d.icon), contentDescription = d.label) },
                label = { Text(d.label) },
                alwaysShowLabel = false,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun Content(
    nav: androidx.navigation.NavHostController,
    container: AppContainer,
    snackbar: SnackbarHostState,
    onOpenRepo: (Repository) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Appearance is collected once per composition and passed down; theme is
    // applied above in MainActivity, layout/density/radius/motion here (M7).
    val appearance by container.preferences.appearance.collectAsState(
        initial = AppearanceSettings(),
    )
    val grid = appearance.layout == HomeLayout.GRID
    val compact = appearance.density == CardDensity.COMPACT
    // Shared install truth + download bridge (feature modules never touch
    // each other directly — the app module wires them).
    val appContext = LocalContext.current
    val installedGateway = remember(appContext) {
        SystemInstalledApps(appContext.packageManager)
    }
    val downloadTracker = remember {
        object : DownloadTracker {
            override fun observe(assetId: Long) = container.downloader.observe(assetId)
            override suspend fun start(
                asset: dev.gstore.model.ReleaseAsset,
                repoFullName: String,
                releaseTag: String,
            ): Boolean = container.downloader.start(
                assetId = asset.id,
                repoFullName = repoFullName,
                releaseTag = releaseTag,
                rawFilename = asset.filename,
                url = asset.downloadUrl,
                expectedSize = asset.size,
                expectedDigest = asset.digest,
            )
            override suspend fun cancel(assetId: Long) =
                container.downloader.cancel(assetId)
        }
    }
    // Tab VMs live at shell scope (activity ViewModelStore), not per
    // destination: switching tabs never resets query, sort, filter, or
    // catalog state. Screens stay destination-scoped, so entry effects
    // (installed refresh, permission re-check) still re-fire on return.
    val homeVm: HomeViewModel = viewModel(
        factory = vmFactory { HomeViewModel(container.catalog, container.preferences, container.catalogClient, container.tokens, container.signals.signals) },
    )
    val searchVm: SearchViewModel = viewModel(
        factory = vmFactory { SearchViewModel(container.catalog) },
    )
    val downloadsVm: DownloadsViewModel = viewModel(
        factory = vmFactory {
            DownloadsViewModel(
                container.downloader,
                installedGateway,
                container.catalog,
                container.tokens,
                container.preferences,
            )
        },
    )
    NavHost(navController = nav, startDestination = Home, modifier = modifier) {
        composable<Home> {
            HomeScreen(
                vm = homeVm,
                onOpenRepo = onOpenRepo,
                onOpenCategory = { nav.navigate(CategoryFull(it.name)) },
                onOpenLogin = { nav.navigate(Settings) },
                onLogout = {
                    scope.launch {
                        container.tokens.clear()
                        container.github.token = null
                    }
                },
                snackbar = snackbar,
                grid = grid,
                compact = compact,
                cardRadiusDp = appearance.cardRadiusDp,
                reduceMotion = appearance.reduceMotion,
            )
        }
        composable<Search> {
            SearchScreen(
                vm = searchVm,
                onOpenRepo = onOpenRepo,
                snackbar = snackbar,
                grid = grid,
                compact = compact,
                cardRadiusDp = appearance.cardRadiusDp,
            )
        }
        composable<Downloads> {
            val vm = downloadsVm
            val dlState by vm.state.collectAsState()
            val context = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            var gateFor by remember { mutableStateOf<DownloadRecord?>(null) }
            // Re-check the per-source install grant every time we return
            // (e.g. from system settings) — flow D.
            var canInstall by remember {
                mutableStateOf(container.installer.canRequestInstalls())
            }
            androidx.compose.runtime.DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        canInstall = container.installer.canRequestInstalls()
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            fun fireInstall(record: DownloadRecord) {
                val path = record.filePath
                if (path == null || !File(path).exists()) {
                    scope.launch { snackbar.showSnackbar("File is no longer on disk") }
                    return
                }
                try {
                    context.startActivity(container.installer.installIntent(File(path)))
                } catch (e: Exception) {
                    scope.launch {
                        snackbar.showSnackbar("No system installer found: ${e.message}")
                    }
                }
            }
            val gate = gateFor
            if (gate != null) {
                InstallerPermissionScreen(
                    fileName = gate.filename,
                    canInstall = canInstall,
                    onOpenSettings = {
                        context.startActivity(container.installer.settingsIntent())
                    },
                    onInstall = { fireInstall(gate); gateFor = null },
                    onCancel = { gateFor = null },
                )
            } else {
                DownloadsScreen(
                    state = dlState,
                    onCancel = { vm.cancel(it) },
                    onRetry = { vm.retry(it) },
                    onForget = { vm.forget(it) },
                    onInstall = { record ->
                        val path = record.filePath
                        if (path == null || !File(path).exists()) {
                            scope.launch { snackbar.showSnackbar("File is no longer on disk") }
                        } else if (container.installer.canRequestInstalls()) {
                            fireInstall(record)
                        } else {
                            gateFor = record
                        }
                    },
                    onBrowseRepo = { fullName ->
                        // Details resolves id 0 via on-demand sync by fullName.
                        val owner = fullName.substringBefore('/', fullName)
                        onOpenRepo(
                            Repository(
                                id = 0,
                                fullName = fullName,
                                owner = owner,
                                avatarUrl = null,
                                description = null,
                                stars = 0,
                                forks = 0,
                                updatedAt = null,
                            ),
                        )
                    },
                    onLaunch = { packageName ->
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(packageName)
                        if (intent != null) context.startActivity(intent)
                        else scope.launch { snackbar.showSnackbar("No launcher entry") }
                    },
                    onUninstall = { packageName ->
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_DELETE,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        }.onFailure {
                            scope.launch { snackbar.showSnackbar("Could not open uninstall") }
                        }
                    },
                    onDownloadFavorite = vm::downloadFavorite,
                    onRemoveManual = vm::removeManualRepo,
                    onRefreshInstalled = vm::refreshInstalled,
                    onSort = vm::setSort,
                    onOpenLogin = { nav.navigate(Settings) },
                    onDismissLoginPrompt = vm::dismissLoginPrompt,
                    snackbar = snackbar,
                    onDismissNotice = vm::dismissNotice,
                )
            }
        }
        composable<Settings> {
            val vm: SettingsViewModel = viewModel(
                factory = vmFactory {
                    SettingsViewModel(container.preferences, container.tokens, container.github)
                },
            )
            SettingsScreen(
                appearance = appearance,
                vm = vm,
                appVersion = BuildConfig.VERSION_NAME,
            )
        }
        composable<CategoryFull> { backStack ->
            val name = backStack.toRoute<CategoryFull>().name
            val category = runCatching { AppCategory.valueOf(name) }.getOrNull()
            if (category == null) {
                LaunchedEffect(name) { nav.popBackStack() }
            } else {
                val homeState by homeVm.state.collectAsState()
                val items = homeState.catalog
                    .filter { categoryOf(it.repo.topics) == category }
                    .sortedByDescending { it.repo.stars }
                CategoryFullScreen(
                    category = category,
                    items = items,
                    favorites = homeState.favoriteIds,
                    syncing = homeState.syncingCatalog,
                    grid = grid,
                    compact = compact,
                    cardRadiusDp = appearance.cardRadiusDp,
                    reduceMotion = appearance.reduceMotion,
                    onBack = { nav.popBackStack() },
                    onOpen = onOpenRepo,
                    onToggleFavorite = homeVm::toggleFavorite,
                )
            }
        }
        composable<Details> { backStack ->
            val route = backStack.toRoute<Details>()
            val vm: DetailsViewModel = viewModel(
                key = "details-${route.repoId}-${route.fullName}",
                factory = vmFactory {
                    DetailsViewModel(route.repoId, route.fullName, container.catalog, downloadTracker, installedGateway)
                },
            )
            val state by vm.state.collectAsState()
            val detailsContext = LocalContext.current
            var detailsGateFor by remember { mutableStateOf<DownloadRecord?>(null) }
            fun fireDetailsInstall(record: DownloadRecord) {
                val path = record.filePath
                if (path == null || !File(path).exists()) {
                    scope.launch { snackbar.showSnackbar("File is no longer on disk") }
                    return
                }
                try {
                    detailsContext.startActivity(container.installer.installIntent(File(path)))
                } catch (e: Exception) {
                    scope.launch {
                        snackbar.showSnackbar("No system installer found: ${e.message}")
                    }
                }
            }
            DetailsScreen(
                state = state,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onToggleFavorite = { vm.toggleFavorite() },
                onRefresh = { vm.refresh() },
                onSelectRelease = { vm.selectRelease(it) },
                // The hero button owns the whole download lifecycle in place:
                // nothing here ever navigates away from details.
                onHeroDownload = { asset -> vm.heroDownload(asset) },
                onHeroCancel = { vm.heroCancel(it) },
                onHeroInstall = { record ->
                    val path = record.filePath
                    if (path == null || !File(path).exists()) {
                        scope.launch { snackbar.showSnackbar("File is no longer on disk") }
                    } else if (container.installer.canRequestInstalls()) {
                        fireDetailsInstall(record)
                    } else {
                        detailsGateFor = record
                    }
                },
                onHeroUninstall = { packageName ->
                    try {
                        detailsContext.startActivity(
                            Intent(
                                Intent.ACTION_DELETE,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    } catch (e: Exception) {
                        scope.launch { snackbar.showSnackbar("Cannot uninstall: ${e.message}") }
                    }
                },
                canOpenApp = { packageName ->
                    detailsContext.packageManager.getLaunchIntentForPackage(packageName) != null
                },
                onOpenApp = { packageName ->
                    try {
                        val launch = detailsContext.packageManager.getLaunchIntentForPackage(packageName)
                        if (launch != null) detailsContext.startActivity(launch)
                        else scope.launch { snackbar.showSnackbar("This app cannot be opened") }
                    } catch (e: Exception) {
                        scope.launch { snackbar.showSnackbar("Cannot open: ${e.message}") }
                    }
                },
                onOpenReadme = {
                    val repo = state.repo
                    if (repo != null) {
                        val parts = repo.fullName.split('/')
                        if (parts.size == 2) nav.navigate(Readme(parts[0], parts[1]))
                    }
                },
                cardRadiusDp = appearance.cardRadiusDp,
                reduceMotion = appearance.reduceMotion,
            )
            val detailsGate = detailsGateFor
            if (detailsGate != null) {
                InstallerPermissionScreen(
                    fileName = detailsGate.filename,
                    canInstall = container.installer.canRequestInstalls(),
                    onOpenSettings = {
                        detailsContext.startActivity(container.installer.settingsIntent())
                    },
                    onInstall = { fireDetailsInstall(detailsGate); detailsGateFor = null },
                    onCancel = { detailsGateFor = null },
                )
            }
        }
        composable<Readme> { backStack ->
            val route = backStack.toRoute<Readme>()
            val vm: ReadmeViewModel = viewModel(
                key = "readme-${route.owner}/${route.name}",
                factory = vmFactory {
                    ReadmeViewModel(route.owner, route.name, container.readme, container.catalog)
                },
            )
            val state by vm.state.collectAsState()
            ReadmeScreen(
                state = state,
                onRefresh = { vm.refresh(it) },
                onDismissNotice = { vm.dismissNotice() },
            )
        }
    }
}

