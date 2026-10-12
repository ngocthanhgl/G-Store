package dev.gstore.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.gstore.data.CatalogRepository
import dev.gstore.database.DownloadRecord
import dev.gstore.designsystem.GStoreTheme
import dev.gstore.downloads.DownloadsScreen
import dev.gstore.downloads.DownloadsViewModel
import dev.gstore.favorites.FavoritesScreen
import dev.gstore.favorites.FavoritesViewModel
import dev.gstore.details.DetailsScreen
import dev.gstore.details.DetailsViewModel
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
import dev.gstore.settings.ThemeEditorScreen
import dev.gstore.ui.EmptyContent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object Home
@Serializable data object Search
@Serializable data object Downloads
@Serializable data object Favorites
@Serializable data object Settings
@Serializable data object ThemeEditor
@Serializable data class Details(val repoId: Long, val fullName: String = "")
@Serializable data class Readme(val owner: String, val name: String)

private data class TopDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
)

private val DESTINATIONS = listOf(
    TopDestination(Home, "Home", Icons.Filled.Home),
    TopDestination(Search, "Search", Icons.Filled.Search),
    TopDestination(Downloads, "Downloads", Icons.Filled.KeyboardArrowDown),
    TopDestination(Favorites, "Favorites", Icons.Filled.Favorite),
    TopDestination(Settings, "Settings", Icons.Filled.Settings),
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
                    navigate = { nav.navigate(it) { popToRoot(nav) } },
                    )
                Content(
                    nav = nav,
                    container = container,
                    snackbar = snackbar,
                    onOpenRepo = onOpenRepo,
                    modifier = Modifier
                        .weight(1f)
                        // Rail owns the full height; content takes top +
                        // horizontal safe insets (status bar, cutout).
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                            ),
                        ),
                )
            }
        } else {
            Scaffold(
                // Content takes top + horizontal safe insets; the bottom bar
                // owns the navigation-bar area and pads itself.
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            DESTINATIONS.forEach { d ->
                                val selected = entry?.destination?.hasRoute(d.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        nav.navigate(d.route) { popToRoot(nav) }
                                    },
                                    icon = { Icon(d.icon, contentDescription = d.label) },
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
                Content(nav = nav, container = container, snackbar = snackbar, onOpenRepo = onOpenRepo, modifier = Modifier.padding(padding))
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
                icon = { Icon(d.icon, contentDescription = d.label) },
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
    NavHost(navController = nav, startDestination = Home, modifier = modifier) {
        composable<Home> {
            val vm: HomeViewModel = viewModel(
                factory = vmFactory { HomeViewModel(container.catalog, container.preferences, container.catalogClient) },
            )
            HomeScreen(
                vm = vm,
                onOpenRepo = onOpenRepo,
                snackbar = snackbar,
                grid = grid,
                compact = compact,
                cardRadiusDp = appearance.cardRadiusDp,
                reduceMotion = appearance.reduceMotion,
            )
        }
        composable<Search> {
            val vm: SearchViewModel = viewModel(
                factory = vmFactory { SearchViewModel(container.catalog) },
            )
            SearchScreen(
                vm = vm,
                onOpenRepo = onOpenRepo,
                snackbar = snackbar,
                grid = grid,
                compact = compact,
                cardRadiusDp = appearance.cardRadiusDp,
            )
        }
        composable<Downloads> {
            val vm: DownloadsViewModel = viewModel(
                factory = vmFactory { DownloadsViewModel(container.downloader) },
            )
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
                )
            }
        }
        composable<Favorites> {
            val vm: FavoritesViewModel = viewModel(
                factory = vmFactory { FavoritesViewModel(container.catalog) },
            )
            FavoritesScreen(
                vm = vm,
                onOpenRepo = onOpenRepo,
                grid = grid,
                compact = compact,
                cardRadiusDp = appearance.cardRadiusDp,
            )
        }
        composable<Settings> {
            val vm: SettingsViewModel = viewModel(
                factory = vmFactory { SettingsViewModel(container.preferences) },
            )
            SettingsScreen(
                appearance = appearance,
                vm = vm,
                appVersion = BuildConfig.VERSION_NAME,
                onOpenThemeEditor = { nav.navigate(ThemeEditor) },
            )
        }
        composable<ThemeEditor> {
            val vm: SettingsViewModel = viewModel(
                factory = vmFactory { SettingsViewModel(container.preferences) },
            )
            ThemeEditorScreen(
                appearance = appearance,
                vm = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable<Details> { backStack ->
            val route = backStack.toRoute<Details>()
            val vm: DetailsViewModel = viewModel(
                key = "details-${route.repoId}-${route.fullName}",
                factory = vmFactory {
                    DetailsViewModel(route.repoId, route.fullName, container.catalog, container.preferences)
                },
            )
            val state by vm.state.collectAsState()
            DetailsScreen(
                state = state,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onToggleFavorite = { vm.toggleFavorite() },
                onRefresh = { vm.refresh() },
                onSelectRelease = { vm.selectRelease(it) },
                onDownload = { asset ->
                    val repo = state.repo
                    val tag = state.releases.firstOrNull { it.id == state.selectedReleaseId }?.tag
                    if (repo != null && tag != null) {
                        scope.launch {
                            val started = container.downloader.start(
                                assetId = asset.id,
                                repoFullName = repo.fullName,
                                releaseTag = tag,
                                rawFilename = asset.filename,
                                url = asset.downloadUrl,
                                expectedSize = asset.size,
                                expectedDigest = asset.digest,
                            )
                            if (started) {
                                nav.navigate(Downloads)
                            } else {
                                snackbar.showSnackbar(
                                    "Already downloading ${asset.filename}",
                                )
                            }
                        }
                    }
                },
                onOpenReadme = {
                    val repo = state.repo
                    if (repo != null) {
                        val parts = repo.fullName.split('/')
                        if (parts.size == 2) nav.navigate(Readme(parts[0], parts[1]))
                    }
                },
            )
        }
        composable<Readme> { backStack ->
            val route = backStack.toRoute<Readme>()
            val vm: ReadmeViewModel = viewModel(
                key = "readme-${route.owner}/${route.name}",
                factory = vmFactory {
                    ReadmeViewModel(route.owner, route.name, container.readme)
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
