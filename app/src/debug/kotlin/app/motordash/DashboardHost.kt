package app.motordash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.motordash.files.FileShareHost
import app.motordash.dashboard.DashboardUiImpl
import app.motordash.hot.HotReloadClient

@Composable
fun DashboardHost(host: FileShareHost) {
    val context = LocalContext.current.applicationContext
    val client = remember { HotReloadClient(context) }
    LaunchedEffect(client) { client.run() }

    var editing by remember { mutableStateOf(false) }
    // Hot reload status and server dialog for the Settings page. Fed into the real host rather than
    // wrapping it with `object : Host by host`: Kotlin's incremental build doesn't regenerate such
    // delegates when Host gains members, which crashed the app with AbstractMethodError.
    LaunchedEffect(host, client) {
        host.onOpenDevSettings = { editing = true }
        snapshotFlow { client.status }.collect { host.devStatusState.value = it }
    }

    val bundled = remember { DashboardUiImpl() }
    val ui = client.ui ?: bundled
    // New classloader = new classes, so restart the dashboard composition on every reload.
    key(ui) { ui.Content(host) }

    if (editing) DevServerDialog(client, onDismiss = { editing = false })
}

@Composable
private fun DevServerDialog(client: HotReloadClient, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(client.config.value.url) }
    var token by remember { mutableStateOf(client.config.value.token) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hot reload server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("Token") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({ client.updateConfig(url.trim(), token.trim()); onDismiss() }) { Text("Connect") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
