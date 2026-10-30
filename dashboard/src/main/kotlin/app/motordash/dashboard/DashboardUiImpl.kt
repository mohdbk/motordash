package app.motordash.dashboard

import androidx.compose.runtime.Composable
import app.motordash.api.DashboardUi
import app.motordash.api.Host

/** Entry point the shell instantiates by name after each hot reload. Keep the no-arg constructor. */
class DashboardUiImpl : DashboardUi {
    @Composable
    override fun Content(host: Host) = Dashboard(host)
}
