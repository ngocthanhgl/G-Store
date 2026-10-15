package dev.gstore.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.ui.AppIcon
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.ThemeMode

private val RowMinHeight = 48.dp

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
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                Text(
                    "Appearance and behavior. Changes apply and save instantly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            val identity by vm.identity.collectAsState()
            val loginBusy by vm.loginBusy.collectAsState()
            val loginError by vm.loginError.collectAsState()
            var showLogin by remember { mutableStateOf(false) }
            SettingGroup("GitHub") {
                if (identity.login.isBlank()) {
                    Text(
                        "Anonymous browsing: 60 requests/hour. Log in to raise " +
                            "it to 5,000 — a token with no scopes is enough.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showLogin = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Log in with token", maxLines = 1)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            avatarUrl = identity.avatarUrl,
                            owner = identity.login,
                            size = 40.dp,
                        )
                        Text(
                            text = identity.login,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        TextButton(onClick = vm::logout) { Text("Logout") }
                    }
                }
                val error = loginError
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = vm::dismissLoginError) { Text("Dismiss") }
                }
                if (loginBusy) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            if (showLogin) {
                LoginDialog(
                    onDismiss = { showLogin = false },
                    onConfirm = {
                        vm.loginWithToken(it)
                        showLogin = false
                    },
                )
            }
        }
        item {
            SettingGroup("Theme") {
                ChoiceRow(
                    options = ThemeMode.entries,
                    selected = appearance.theme,
                    labelOf = ::themeModeLabel,
                    onSelect = vm::setTheme,
                )
                SwitchRow(
                    label = "Dynamic color",
                    supporting = "Follow wallpaper colors on Android 12+",
                    checked = appearance.dynamicColor,
                    onChecked = vm::setDynamicColor,
                )
            }
        }
        item {
            SettingGroup("Layout") {
                SettingLabel("Cards")
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
                // Drag updates locally; DataStore writes once per gesture so a
                // write-storm can never jank or drop the final value.
                var radius by remember(appearance.cardRadiusDp) {
                    mutableFloatStateOf(appearance.cardRadiusDp.toFloat())
                }
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    onValueChangeFinished = { vm.setCardRadius(radius.toInt()) },
                    valueRange = 4f..20f,
                    steps = 15,
                )
            }
        }
        item {
            SettingGroup("Motion") {
                SwitchRow(
                    label = "Reduce motion",
                    supporting = "Expanding lists snap instead of animating",
                    checked = appearance.reduceMotion,
                    onChecked = vm::setReduceMotion,
                )
            }
        }
        item {
            SettingGroup("About") {
                Text(
                    "G Store $appVersion — GitHub-hosted APK releases. Stars and " +
                        "download counts are popularity signals, never safety ratings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun LoginDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log in to GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Create a token (no scopes needed), then paste it here. " +
                        "It stays encrypted on this device.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    uriHandler.openUri("https://github.com/settings/tokens/new")
                }) {
                    Text("Open github.com/settings/tokens")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    label = { Text("Personal access token") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("Log in")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            content()
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
) {
    // FlowRow wraps on narrow screens instead of pushing chips off-screen.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), maxLines = 1) },
            )
        }
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
