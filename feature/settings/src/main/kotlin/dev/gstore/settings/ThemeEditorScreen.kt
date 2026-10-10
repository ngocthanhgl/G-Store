package dev.gstore.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.designsystem.cardShapeFor

/**
 * Theme editor (M7): accent swatches + live preview rendered in the CURRENT
 * theme, so what you see is what every screen uses.
 */
@Composable
fun ThemeEditorScreen(
    appearance: AppearanceSettings,
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to settings",
                    )
                }
                Text("Theme editor", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Text(
                "Accent",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ACCENT_OPTIONS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = appearance.accentArgb == option.argb,
                                onClick = { vm.setAccent(option.argb) },
                                label = { Text(option.name) },
                                leadingIcon = {
                                    Swatch(color = option.argb?.let { Color(it) })
                                },
                            )
                        }
                    }
                }
            }
            Text(
                "Automatic follows wallpaper colors when dynamic color is on, " +
                    "otherwise the baseline seed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                "Preview",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Card(shape = cardShapeFor(appearance.cardRadiusDp)) {
                Column(
                    modifier = Modifier.padding(
                        if (appearance.density ==
                            dev.gstore.datastore.CardDensity.COMPACT
                        ) {
                            8.dp
                        } else {
                            12.dp
                        },
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("owner / example-app", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Cards, radius, density, and accent apply everywhere at once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {}) { Text("Sample action") }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color?) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.primary),
    )
}
