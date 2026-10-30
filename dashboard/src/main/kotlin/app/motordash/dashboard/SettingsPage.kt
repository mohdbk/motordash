package app.motordash.dashboard

import app.motordash.api.StorageLocation
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import app.motordash.api.API_VERSION
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motordash.api.Host
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import app.motordash.api.UpdateState.Status as UpdateStatus

/** Everyday settings. Device and developer details live one tap away in [TechnicalPage]. */
@Composable
internal fun SettingsPage(
    host: Host,
    address: String?,
    language: LanguageSetting,
    landscape: Boolean,
    onPickAddress: (String) -> Unit,
    onTechnical: () -> Unit,
) {
    val s = LocalStrings.current
    val server by host.server
    val folder by host.folder
    val messages by host.messages

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        DashCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(Modifier.size(72.dp))
                Spacer(Modifier.width(20.dp))
                Column {
                    val versionName = rememberVersionName()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Wordmark(size = 30.sp)
                        if (isBeta(versionName)) {
                            Spacer(Modifier.width(14.dp))
                            BetaBadge(s.beta)
                        }
                    }
                    Text(
                        s.version(versionName),
                        color = Palette.TextSecondary,
                        fontSize = 17.sp,
                    )
                }
            }
        }

        TwoColumns(
            landscape,
            left = {
                Section(s.general) {
                    SettingRow(s.language) {
                        Chip(s.languageAuto, active = language.choice == Language.Auto, monospace = false) { language.set(Language.Auto) }
                        // Each language is named in itself, so it's recognisable whatever is showing.
                        Chip("English", active = language.choice == Language.English, monospace = false) { language.set(Language.English) }
                        Chip("العربية", active = language.choice == Language.Arabic, monospace = false) { language.set(Language.Arabic) }
                    }
                }

                Section(s.fileSharing) {
                    // Several networks (e.g. Wi-Fi + hotspot): pick the one the device is on.
                    when {
                        !server.running -> SettingRow(s.addressInQr, server.error ?: s.serverNotRunning)
                        server.addresses.isEmpty() -> SettingRow(s.addressInQr, s.noNetworkShort)
                        else -> SettingRow(s.addressInQr) {
                            for (a in server.addresses) {
                                Chip("$a:${server.port}", active = a == address) { onPickAddress(a) }
                            }
                        }
                    }
                    val storage by host.storage
            var changingFolder by remember { mutableStateOf(false) }
            SettingRow(s.savedTo, s.savedIn(storage.path, formatSize(folder.freeBytes))) {
                ActionButton(s.change, { changingFolder = true }, primary = false)
            }
            if (changingFolder) StorageDialog(storage, onSave = host::changeStorage, onDismiss = { changingFolder = false })
                    SettingRow(
                        s.messagesFromDevices,
                        s.messageCount(messages.size),
                    ) {
                        if (messages.isNotEmpty()) ActionButton(s.clear, { messages.forEach(host::dismiss) }, primary = false)
                    }
                }
            },
            right = {
                UpdatesSection(host)

                Section(s.about) {
                    // Where to report problems; there's no ADB on the cars to see logs.
                    SettingRow(s.feedback, s.isolate(FEEDBACK_URL.removePrefix("https://"))) {
                        ActionButton(s.open, { host.openLink(FEEDBACK_URL) }, primary = false)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onTechnical)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.technicalDetails, color = Palette.TextPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text(
                            // Developer (hot reload) only exists in debug builds.
                            s.technicalSummary(host.devStatus.value != null),
                            color = Palette.TextSecondary,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextSecondary)
                    }
                }
            },
        )
    }
}

@Composable
private fun UpdatesSection(host: Host) {
    val update by host.update
    if (update.status == UpdateStatus.Disabled) return
    val s = LocalStrings.current
    val time = remember(s.locale) { SimpleDateFormat("HH:mm", s.locale) }
    Section(s.updates) {
        val value = when (update.status) {
            UpdateStatus.Idle, UpdateStatus.Disabled -> s.version(update.currentVersion)
            UpdateStatus.Checking -> s.checking
            UpdateStatus.UpToDate -> s.upToDate(time.format(Date(update.lastChecked)))
            UpdateStatus.Available -> s.versionAvailable(update.latestVersion.orEmpty())
            UpdateStatus.Downloading -> s.downloading(update.latestVersion.orEmpty(), (update.progress * 100).toInt())
            UpdateStatus.ReadyToInstall -> s.readyToInstall(update.latestVersion.orEmpty())
            UpdateStatus.Failed -> update.error ?: s.somethingWrong
        }
        SettingRow("MotorDash ${update.currentVersion}", value) {
            when (update.status) {
                UpdateStatus.Available -> ActionButton(s.update, host::installUpdate)
                UpdateStatus.ReadyToInstall -> ActionButton(s.install, host::installUpdate)
                UpdateStatus.Checking, UpdateStatus.Downloading -> {}
                else -> ActionButton(if (update.status == UpdateStatus.Failed) s.tryAgain else s.checkNow, host::checkForUpdate, primary = false)
            }
        }
        if (update.status == UpdateStatus.Downloading) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Track)) {
                Box(Modifier.fillMaxWidth(update.progress).fillMaxHeight().background(Palette.Blue))
            }
        }
        val notes = update.notes
        if (notes != null && (update.status == UpdateStatus.Available || update.status == UpdateStatus.ReadyToInstall || update.status == UpdateStatus.Downloading)) {
            Text(s.whatsNew, color = Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            Text(notes, color = Palette.TextPrimary, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Device info, server details and hot reload: useful for debugging, noise for passengers. */
@Composable
internal fun TechnicalPage(host: Host, landscape: Boolean, onBack: () -> Unit) {
    val s = LocalStrings.current
    val server by host.server
    val devStatus by host.devStatus

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onBack).padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Palette.TextPrimary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(s.settings, color = Palette.TextPrimary, fontSize = 18.sp)
        }

        TwoColumns(
            landscape,
            left = {
                Section(s.thisApp) {
                    val info = rememberPackageInfo()
                    SettingRow(s.versionLabel, s.appVersion(info.versionName ?: "?", info.versionCodeCompat))
                }

                Section(s.thisDisplay) {
                    SettingRow(s.androidVersion, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    SettingRow(s.device, listOf(Build.MANUFACTURER, Build.MODEL).distinct().joinToString(" "))
                    SettingRow(s.screen, rememberScreenDescription())
                }
            },
            right = {
                Section(s.fileServer) {
                    SettingRow(
                        s.status,
                        when {
                            !server.running -> server.error ?: s.notRunning
                            server.addresses.isEmpty() -> s.runningNoNetwork(server.port)
                            else -> s.runningOnPort(server.port)
                        },
                    )
                    if (server.addresses.isNotEmpty()) SettingRow(s.addresses, server.addresses.joinToString(", "))
                }

                devStatus?.let { status ->
                    Section(s.developer) {
                        SettingRow(s.hotReload, status) {
                            ActionButton(s.changeServer, host::openDevSettings, primary = false)
                        }
                        // What hot reload payloads must be built against ("APK outdated" when they differ).
                        SettingRow(s.hotReloadApi, API_VERSION.toString())
                    }
                }
            },
        )
    }
}

/** Where to report problems and ideas. */
private const val FEEDBACK_URL = "https://github.com/mohdbk/motordash/issues"

private fun isBeta(versionName: String) = "beta" in versionName.lowercase()

@Composable
private fun BetaBadge(text: String) {
    Text(
        text,
        color = Palette.Blue,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Blue.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Pick Download or Documents and a folder name; offers to bring the existing files along. */
@Composable
private fun StorageDialog(
    current: StorageLocation,
    onSave: (StorageLocation.Base, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var base by remember { mutableStateOf(current.base) }
    var name by remember { mutableStateOf(current.name) }
    var move by remember { mutableStateOf(true) }
    val valid = StorageLocation.isValidName(name.trim())
    val changed = StorageLocation(base, name.trim()).path != current.path
    AppDialog(
        title = s.saveFolderTitle,
        onDismiss = onDismiss,
        confirm = {
            TextButton({ onSave(base, name, move && current.itemCount > 0); onDismiss() }, enabled = valid && changed) {
                Text(s.save, fontSize = 17.sp)
            }
        },
        dismiss = { TextButton(onDismiss) { Text(s.cancel, fontSize = 17.sp) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(s.location, color = Palette.TextSecondary, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in StorageLocation.Base.entries) {
                    Chip(option.dirName, active = option == base, monospace = false) { base = option }
                }
            }
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(s.folderName) },
                singleLine = true,
                isError = !valid,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                StorageLocation(base, name.trim()).path,
                color = Palette.TextSecondary,
                fontSize = 15.sp,
                style = LtrText,
            )
            if (current.itemCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { move = !move }) {
                    Checkbox(move, { move = it })
                    Text(s.moveItems(current.itemCount), color = Palette.TextPrimary, fontSize = 16.sp)
                }
            }
        }
    
    }
}

/** Side by side on a wide screen (fits Settings without scrolling), stacked otherwise. */
@Composable
private fun TwoColumns(landscape: Boolean, left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    if (landscape) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap), content = left)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap), content = right)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
            left()
            right()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    DashCard(Modifier.fillMaxWidth()) {
        Label(title)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Label with custom content (e.g. chips) in place of the value text. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingRow(label: String, content: @Composable FlowRowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.TextSecondary, fontSize = 18.sp, modifier = Modifier.width(220.dp))
        // Chips wrap to a second line when the column is narrow.
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.TextSecondary, fontSize = 18.sp, modifier = Modifier.width(220.dp))
        Text(value, color = Palette.TextPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, primary: Boolean = true) {
    Text(
        text,
        color = if (primary) Color.White else Palette.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) Palette.Blue else Palette.Row)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun rememberPackageInfo(): PackageInfo {
    val context = LocalContext.current
    return remember { context.packageManager.getPackageInfo(context.packageName, 0) }
}

@Composable
private fun rememberVersionName(): String = rememberPackageInfo().versionName ?: "?"

@Suppress("DEPRECATION")
private val PackageInfo.versionCodeCompat: Long
    get() = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

@Composable
private fun rememberScreenDescription(): String {
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    val widthPx = (config.screenWidthDp * density).toInt()
    val heightPx = (config.screenHeightDp * density).toInt()
    return "$widthPx × $heightPx px · ${config.densityDpi} dpi"
}
