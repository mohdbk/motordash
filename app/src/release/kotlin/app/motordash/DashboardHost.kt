package app.motordash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.motordash.files.FileShareHost
import app.motordash.dashboard.DashboardUiImpl

@Composable
fun DashboardHost(host: FileShareHost) {
    remember { DashboardUiImpl() }.Content(host)
}
