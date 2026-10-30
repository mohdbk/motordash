package app.motordash.dashboard

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.Typography
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motordash.api.Host
import app.motordash.api.UpdateState
import androidx.compose.foundation.shape.CircleShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** [Technical] is a sub-page of Settings, reached from it rather than from the nav rail. */
internal enum class Page { Files, Settings, Technical }

private fun Page.title(s: Strings) = when (this) {
    Page.Files -> s.files
    Page.Settings -> s.settings
    Page.Technical -> s.technicalDetails
}

@Composable
internal fun Dashboard(host: Host, initialPage: Page = Page.Files, language: Language? = null) {
    var page by remember { mutableStateOf(initialPage) }
    // Address put in the QR code, picked on the Settings page when there are several networks.
    var picked by remember { mutableStateOf<String?>(null) }
    val addresses = host.server.value.addresses
    val address = picked?.takeIf { it in addresses } ?: addresses.firstOrNull()
    val languageSetting = rememberLanguageSetting()
    remember(language) { language?.let(languageSetting::set) } // screenshot tests
    val strings = languageSetting.strings()
    val font = rememberAppFont()
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Palette.Blue, background = Color.Black),
        typography = font?.let { Typography().withFont(it) } ?: Typography(),
    ) {
      CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides if (strings.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
      ) {
        Row(Modifier.fillMaxSize().background(Palette.Background)) {
            val update by host.update
            val updateWaiting = update.status == UpdateState.Status.Available || update.status == UpdateState.Status.ReadyToInstall
            NavRail(page, settingsBadge = updateWaiting, onSelect = { page = it })
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(top = 20.dp, end = 24.dp, bottom = 24.dp)) {
                val landscape = maxWidth > maxHeight
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                    StatusBar(page.title(strings))
                    when (page) {
                        Page.Files -> FilesPage(host, address, landscape)
                        Page.Settings -> SettingsPage(host, address, languageSetting, landscape, onPickAddress = { picked = it }, onTechnical = { page = Page.Technical })
                        Page.Technical -> TechnicalPage(host, landscape, onBack = { page = Page.Settings })
                    }
                }
            }
        }
      }
    }
}

/** DiLink-style dock: logo on top, one tile per page. */
@Composable
private fun NavRail(current: Page, settingsBadge: Boolean, onSelect: (Page) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(112.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LogoMark(Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        NavItem(LocalStrings.current.files, current == Page.Files, { onSelect(Page.Files) }) { tint -> FolderIcon(tint, Modifier.size(30.dp)) }
        NavItem(LocalStrings.current.settings, current == Page.Settings || current == Page.Technical, { onSelect(Page.Settings) }, badge = settingsBadge) { tint ->
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit, badge: Boolean = false, icon: @Composable (Color) -> Unit) {
    val tint = if (selected) Palette.Blue else Palette.TextSecondary
    Column(
        Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Palette.Blue.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            icon(tint)
            // e.g. an update is waiting
            if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(10.dp).clip(CircleShape).background(Palette.Green))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = tint, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun StatusBar(title: String) {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000)
        }
    }
    val locale = LocalStrings.current.locale
    val time = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val pattern = LocalStrings.current.datePattern
    val date = remember(locale, pattern) { SimpleDateFormat(pattern, locale) }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(date.format(now), color = Palette.TextSecondary, fontSize = 18.sp)
        Spacer(Modifier.width(16.dp))
        Text(time.format(now), color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}
