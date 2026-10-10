package dev.gstore.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.ThemeMode

private val RowMinHeight = 48.dp

/**
 * Appearance + behavior settings (M7). Every control writes through
 * [SettingsViewModel] to DataStore and is restored before first frame (flow E).
 */
@Composable
fun SettingsScreen(
    appearance: AppearanceSettings,
    vm: SettingsViewModel,
    onOpenThemeEditor: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ScreenTitle("Settings") }
        item {
            SettingGroup("Theme") {
                ChoiceRow(
                    label = "Mode",
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
                ChoiceRow(
                    label = "Cards",
                    options = HomeLayout.entries,
                    selected = appearance.layout,
                    labelOf = ::layoutLabel,
                    onSelect = vm::setLayout,
                )
                ChoiceRow(
                    label = "Density",
                    options = CardDensity.entries,
                    selected = appearance.density,
                    labelOf = ::densityLabel,
                    onSelect = vm::setDensity,
                )
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Corner radius",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${appearance.cardRadiusDp}dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            SettingGroup("Customize") {
                TextButton(onClick = onOpenThemeEditor) {
                    Text("Open theme editor")
                }
                TextButton(onClick = vm::resetAll) {
                    Text("Reset appearance to defaults")
                }
            }
        }
        item {
            SettingGroup("About") {
                Text(
                    "G Store shows GitHub-hosted APK releases. Stars and " +
                        "download counts are popularity signals, never safety ratings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = RowMinHeight)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(labelOf(option)) },
                )
            }
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
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
