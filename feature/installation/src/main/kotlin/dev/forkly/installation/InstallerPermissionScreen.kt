package dev.forkly.installation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

/**
 * Explanatory gate before the system unknown-sources settings page (flow D).
 * Rendered only when [canInstall] is false; the settings page opens solely
 * from [onOpenSettings], and the caller re-checks permission on return.
 */
@Composable
fun InstallerPermissionScreen(
    fileName: String,
    canInstall: Boolean,
    onOpenSettings: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier.fillMaxWidth(),
                shape = dev.forkly.designsystem.panelShape(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Install downloaded APK?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "“$fileName” was downloaded from its GitHub release page. " +
                            "Android installs it through the system package installer — " +
                            "Forkly never installs apps silently.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (canInstall) {
                        Text(
                            "This app is allowed to request installations. You can review " +
                                "every permission on the system installer screen.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onInstall) { Text("Open installer") }
                            TextButton(onClick = onCancel) { Text("Cancel") }
                        }
                    } else {
                        Text(
                            "Android currently blocks this app from requesting installations. " +
                                "You can grant it in system settings — that page is opened " +
                                "only if you choose to, and nothing changes until you confirm there.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onOpenSettings) { Text("Open settings") }
                            OutlinedButton(onClick = onCancel) { Text("Not now") }
                        }
                    }
                }
            }
        }
    }
}
