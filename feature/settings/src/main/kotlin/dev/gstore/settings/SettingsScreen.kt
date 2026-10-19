@file:OptIn(ExperimentalMaterial3Api::class)

package dev.gstore.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.gstore.ui.safeTop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.ui.AppIcon
import dev.gstore.ui.AuroraSectionHeader
import dev.gstore.model.AppRadius
import dev.gstore.ui.Fluent
import dev.gstore.ui.GStoreSpace
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.datastore.AppIconStyle
import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.ThemeMode

private val RowMinHeight = GStoreSpace.TouchTarget

/**
 * Appearance + behavior settings. Every control writes through
 * [SettingsViewModel] to DataStore and is restored before first frame
 * (flow E) — there is no Save button because nothing can stay unsaved.
 * Groups are tonal cards; every row is at least 48dp; every text truncates.
 */
@Composable
fun SettingsScreen(
    appearance: AppearanceSettings,
    vm: SettingsViewModel,
    appVersion: String,
) {
    val identity by vm.identity.collectAsState()
    val loginBusy by vm.loginBusy.collectAsState()
    val loginError by vm.loginError.collectAsState()
    // Hoisted out of the list on purpose. A LazyColumn disposes item
    // composition when the item scrolls out of view, which silently reverted
    // showLogin to false mid-typing; rememberSaveable also survives rotation.
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    // Only a completed submit may dismiss this dialog. Keying the close on
    // "identity exists" was wrong twice over: GitHubIdentity is non-nullable
    // (logged out == GitHubIdentity(login = "")), so that condition is always
    // true and the dialog closed the instant it opened.
    LaunchedEffect(identity.login) {
        if (submitted && identity.login.isNotBlank()) showLogin = false
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GStoreSpace.Gutter)
            .safeTop(),
        verticalArrangement = Arrangement.spacedBy(GStoreSpace.Block),
    contentPadding = PaddingValues(bottom = GStoreSpace.Block),
    ) {
        item {
            SettingGroup("Account", topGap = false) {
                if (identity.login.isBlank()) {
                    Text(
                        "Anonymous: up to 6 manual refreshes per minute. Logged in: no app-imposed quota (GitHub limits still apply); refresh has a 10-second cooldown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = {
                            submitted = false
                            showLogin = true
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = GStoreSpace.TouchTarget),
                    ) { Text("Log in with token", maxLines = 1) }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Medium),
                    ) {
                        AppIcon(
                            avatarUrl = identity.avatarUrl,
                            owner = identity.login,
                            size = 40.dp,
                            circular = true,
                        )
                        Text(
                            text = identity.login,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = vm::logout,
                            modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                        ) { Text("Logout") }
                    }
                }
                loginError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = vm::dismissLoginError) { Text("Dismiss") }
                }
                if (loginBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        item {
            SettingGroup("Theme") {
                ChoiceRow(
                    options = ThemeMode.entries,
                    selected = appearance.theme,
                    labelOf = ::themeModeLabel,
                    onSelect = vm::setTheme,
                    singleLine = true,
                )
                SwitchRow(
                    label = "Dynamic color",
                    supporting = "Follow wallpaper colors on Android 12+ unless a custom accent is selected",
                    checked = appearance.dynamicColor,
                    onChecked = vm::setDynamicColor,
                )
                SettingLabel("Accent color")
                AccentSwatches(
                    selectedArgb = appearance.accentArgb,
                    onSelect = vm::setAccentArgb,
                )
                SettingLabel("App icon")
                ChoiceRow(
                    options = AppIconStyle.entries,
                    selected = appearance.appIcon,
                    labelOf = ::appIconLabel,
                    onSelect = vm::setAppIcon,
                )
                val iconResourceName = when (appearance.appIcon) {
                    AppIconStyle.CLASSIC -> "ic_launcher"
                    AppIconStyle.RED_LIGHT -> "ic_launcher_red_light"
                    AppIconStyle.RED_DARK -> "ic_launcher_red_dark"
                }
                val appContext = androidx.compose.ui.platform.LocalContext.current
                val iconResourceId = androidx.compose.runtime.remember(appearance.appIcon) {
                    appContext.resources.getIdentifier(iconResourceName, "mipmap", appContext.packageName)
                }
                if (iconResourceId != 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { context ->
                                android.widget.ImageView(context).apply {
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            update = { view ->
                                view.setImageResource(iconResourceId)
                                view.contentDescription = "${appIconLabel(appearance.appIcon)} app icon preview"
                            },
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            "Preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SettingLabel("Layout")
                ChoiceRow(
                    options = HomeLayout.entries,
                    selected = appearance.layout,
                    labelOf = ::layoutLabel,
                    onSelect = vm::setLayout,
                )
                SettingLabel("Density")
                ChoiceRow(
                    options = CardDensity.entries,
                    selected = appearance.density,
                    labelOf = ::densityLabel,
                    onSelect = vm::setDensity,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = RowMinHeight),
                ) {
                    Text(
                        "Corner radius",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${appearance.cardRadiusDp}dp",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    "Cards and surfaces. Buttons and chips stay fully round.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Drag updates locally; DataStore writes once per gesture so a
                // write-storm can never jank or drop the final value.
                var radius by remember(appearance.cardRadiusDp) {
                    mutableFloatStateOf(appearance.cardRadiusDp.toFloat())
                }
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    onValueChangeFinished = { vm.setCardRadius(radius.toInt()) },
                    valueRange = AppRadius.MIN.toFloat()..AppRadius.MAX.toFloat(),
                    steps = AppRadius.MAX - AppRadius.MIN - 1,
                    // Pill track: Slider has no shape param, so the track
                    // slot carries the rounded bar instead of M3's thin line.
                    // The slot also hosts the 20dp thumb, so it must be at
                    // least thumb-sized — clipping at 12dp flattened it.
                    track = { state ->
                        SliderDefaults.Track(
                            sliderState = state,
                            modifier = Modifier.height(20.dp).clip(CircleShape),
                        )
                    },
                )
                SettingLabel("Motion")
                SwitchRow(
                    label = "Reduce motion",
                    supporting = "Expanding lists snap instead of animating",
                    checked = appearance.reduceMotion,
                    onChecked = vm::setReduceMotion,
                )
            }
        }
        item {
            SettingGroup("Info") {
                SettingLabel("Release behavior")
                SwitchRow(
                    label = "Include prereleases",
                    supporting = "Off means stable releases only for download and update checks",
                    checked = appearance.includePrereleases,
                    onChecked = vm::setIncludePrereleases,
                )
                SettingLabel("About G Store")
                Text(
                    "G Store $appVersion — GitHub-hosted APK releases. Stars and " +
                        "download counts are popularity signals, never safety ratings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showLogin) {
        LoginDialog(
            busy = loginBusy,
            onDismiss = { if (!loginBusy) showLogin = false },
            onConfirm = {
                submitted = true
                vm.loginWithToken(it)
            },
        )
    }
}

private val accentColors = listOf(
    "Violet" to 0xFF6750A4.toInt(),
    "Blue" to 0xFF0056D2.toInt(),
    "Teal" to 0xFF006A60.toInt(),
    "Green" to 0xFF386A20.toInt(),
    "Orange" to 0xFF8C4A00.toInt(),
    "Red" to 0xFFBA1A1A.toInt(),
    "Pink" to 0xFF9C405D.toInt(),
    "Indigo" to 0xFF4355B9.toInt(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(selectedArgb: Int?, onSelect: (Int?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
    ) {
        AccentSwatch(
            label = "Automatic",
            argb = MaterialTheme.colorScheme.primary.toArgb(),
            selected = selectedArgb == null,
            onClick = { onSelect(null) },
        )
        accentColors.forEach { (label, argb) ->
            AccentSwatch(
                label = label,
                argb = argb,
                selected = selectedArgb == argb,
                onClick = { onSelect(argb) },
            )
        }
    }
}

@Composable
private fun AccentSwatch(label: String, argb: Int, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(GStoreSpace.TouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$label accent"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(2.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Fluent.Checkmark, contentDescription = null, tint = Color.White)
        }
    }
}

/**
 * Token login: the user pastes a Personal Access Token created at
 * github.com/settings/tokens (no scopes needed). The token is validated
 * before storage and kept encrypted on-device — it never leaves the hop
 * to api.github.com.
 */
@Composable
private fun LoginDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log in to GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GStoreSpace.Block)) {
                Text(
                    "Create a token (no scopes needed), then paste it here. " +
                        "It stays encrypted on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(
                    onClick = {
                        uriHandler.openUri("https://github.com/settings/tokens/new")
                    },
                    modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                ) {
                    Text(
                        "Open github.com/settings/tokens",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Personal access token") },
                    singleLine = true,
                    shape = CircleShape,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && !busy,
                modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Log in")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingGroup(
    title: String,
    topGap: Boolean = true,
    content: @Composable () -> Unit,
) {
    // The header carries its own 24dp-above / 8dp-below rhythm.
    Column {
        // Shared header so group titles cannot drift from every other screen:
        // titleLarge, no primary tint, 24dp above / 8dp below.
        AuroraSectionHeader(title = title, topGap = topGap)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = dev.gstore.designsystem.panelShape(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(GStoreSpace.Card),
                verticalArrangement = Arrangement.spacedBy(GStoreSpace.Block),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    singleLine: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                shape = CircleShape,
                label = { Text(labelOf(option), maxLines = 1) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            painter = Fluent.Checkmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
            )
        }
    }
    if (singleLine) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        ) { content() }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        ) { content() }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}