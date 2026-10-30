package app.motordash.dashboard

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motordash.api.FileEntry
import app.motordash.api.Folder
import app.motordash.api.Host
import app.motordash.api.ServerState
import app.motordash.api.TextMessage
import app.motordash.api.Transfer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay

/** Files page: device messages on top, then the QR code to connect a device and the file list. */
@Composable
internal fun FilesPage(host: Host, address: String?, landscape: Boolean) {
    val server by host.server
    val messages by host.messages
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Gap)) {
        messages.firstOrNull()?.let { MessageBanner(host, it, more = messages.size - 1) }
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                ReceiveCard(host, server, address, Modifier.weight(1f).fillMaxHeight())
                FilesCard(host, server, address, Modifier.weight(1.7f).fillMaxHeight())
            }
        } else {
            ReceiveCard(host, server, address, Modifier.weight(1f).fillMaxWidth())
            FilesCard(host, server, address, Modifier.weight(1.3f).fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------- Receive (QR)

@Composable
private fun ReceiveCard(host: Host, server: ServerState, address: String?, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val devices by host.devicesConnected
    DashCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(s.receiveFiles, modifier = Modifier.weight(1f))
            if (server.running && address != null) {
                // Ready, or better: a device has the page open right now.
                StatusDot(if (devices > 0) Palette.Blue else Palette.Green, if (devices > 0) s.devicesConnected(devices) else s.ready)
            }
        }
        when {
            !server.running -> Message(s.serverNotRunning, server.error ?: s.starting, Modifier.weight(1f))
            address == null -> Message(s.noNetwork, s.connectHint, Modifier.weight(1f))
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    QrCode(server.url(address), Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                }
                Text(
                    s.scanToConnect,
                    color = Palette.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(6.dp))
                // For devices that can't scan: the address and PIN to type, in a dialog.
                var showLink by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .clickable { showLink = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Palette.Blue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(s.cantScan, color = Palette.Blue, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
                if (showLink) PinDialog("$address:${server.port}", server.token, onDismiss = { showLink = false })
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PinDialog(address: String, pin: String, onDismiss: () -> Unit) {
    AppDialog(
        title = LocalStrings.current.connectWithoutQr,
        onDismiss = onDismiss,
        confirm = { TextButton(onDismiss) { Text(LocalStrings.current.done, fontSize = 17.sp) } },
    ) {
        Column {
            Text(
                LocalStrings.current.connectWithoutQrHint,
                color = Palette.TextSecondary,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogValue(LocalStrings.current.address, address, Modifier.weight(1.6f))
                DialogValue(LocalStrings.current.pin, pin.chunked(3).joinToString(" "), Modifier.weight(1f))
            }
        }
    
    }
}

@Composable
private fun DialogValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Row).padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Palette.TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Palette.TextPrimary, fontSize = 24.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false, style = LtrText)
    }
}

@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    // Dark-on-light with a quiet zone: the most reliable combination for device cameras.
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / matrix.width
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix[x, y]) {
                        drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Files

@Composable
private fun FilesCard(host: Host, server: ServerState, address: String?, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val folder by host.folder
    val transfers by host.transfers
    val sort = rememberSortSetting()
    val entries = remember(folder.entries, sort.order) { folder.entries.sortedFor(sort.order) }
    val listState = rememberLazyListState()
    LaunchedEffect(sort.order, folder.path) { listState.scrollToItem(0) }
    var sending by remember { mutableStateOf<FileEntry?>(null) }
    DashCard(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Breadcrumbs(folder.path, onBrowse = host::browse, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Text(
                s.itemsFree(folder.entries.size, formatSize(folder.freeBytes)),
                color = Palette.TextSecondary,
                fontSize = 16.sp,
            )
        }
        if (folder.entries.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((order, label) in listOf(SortOrder.Newest to s.sortNewest, SortOrder.Name to s.sortName, SortOrder.Size to s.sortSize)) {
                    Chip(label, active = sort.order == order, monospace = false) { sort.set(order) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (transfers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in transfers) TransferRow(t)
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            folder.entries.isNotEmpty() -> LazyColumn(
                Modifier.weight(1f).scrollIndicator(listState),
                state = listState,
                // Room for the scroll indicator at the end edge.
                contentPadding = PaddingValues(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries, key = { it.path }) { entry ->
                    FileRow(
                        host,
                        entry,
                        // Only files, and only when a device could reach this screen.
                        onSend = if (!entry.isDirectory && address != null && server.running) ({ sending = entry }) else null,
                    )
                }
            }
            folder.path.isEmpty() -> EmptyState(Modifier.weight(1f))
            else -> Message(LocalStrings.current.folderEmpty, "", Modifier.weight(1f))
        }
    }
    sending?.let { entry ->
        if (address != null) SendToDeviceDialog(entry, server.downloadUrl(address, entry), onDismiss = { sending = null })
    }
}

// ---------------------------------------------------------------- Sorting

internal enum class SortOrder { Newest, Name, Size }

/** Folders first, then files in the chosen order. Newest is the default: what just arrived is on top. */
private fun List<FileEntry>.sortedFor(order: SortOrder): List<FileEntry> {
    val byName = compareBy<FileEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    val inOrder = when (order) {
        SortOrder.Newest -> compareByDescending<FileEntry> { it.modified }.then(byName)
        SortOrder.Name -> byName
        SortOrder.Size -> compareByDescending<FileEntry> { it.size }.then(byName)
    }
    return sortedWith(compareBy<FileEntry> { !it.isDirectory }.then(inOrder))
}

/** The chosen sort order, kept across restarts and hot reloads like the language. */
private class SortSetting(context: Context) {
    private val prefs = context.getSharedPreferences("motordash-ui", Context.MODE_PRIVATE)
    var order by mutableStateOf(runCatching { SortOrder.valueOf(prefs.getString("sort", null) ?: "") }.getOrDefault(SortOrder.Newest))
        private set

    fun set(value: SortOrder) {
        order = value
        prefs.edit().putString("sort", value.name).apply()
    }
}

@Composable
private fun rememberSortSetting(): SortSetting {
    val context = LocalContext.current
    return remember { SortSetting(context.applicationContext) }
}

/** A thin thumb at the list's end edge showing where you are, only when the list overflows. */
private fun Modifier.scrollIndicator(state: LazyListState) = drawWithContent {
    drawContent()
    val visible = state.layoutInfo.visibleItemsInfo
    val total = state.layoutInfo.totalItemsCount
    if (visible.isEmpty() || (!state.canScrollForward && !state.canScrollBackward)) return@drawWithContent
    val itemSize = visible.sumOf { it.size }.toFloat() / visible.size + 6.dp.toPx()
    val contentHeight = itemSize * total
    val viewport = size.height
    val scrolled = state.firstVisibleItemIndex * itemSize + state.firstVisibleItemScrollOffset
    val thumb = (viewport * viewport / contentHeight).coerceIn(40.dp.toPx(), viewport)
    val y = (scrolled / (contentHeight - viewport)).coerceIn(0f, 1f) * (viewport - thumb)
    val width = 5.dp.toPx()
    val x = if (layoutDirection == LayoutDirection.Ltr) size.width - width else 0f
    drawRoundRect(Palette.TextMuted, Offset(x, y), Size(width, thumb), CornerRadius(width / 2))
}

/** "All files › Music › Live": each part opens that folder. Replaces a plain "Files" heading. */
@Composable
private fun Breadcrumbs(path: String, onBrowse: (String) -> Unit, modifier: Modifier = Modifier) {
    val parts = if (path.isEmpty()) emptyList() else path.split('/')
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (parts.isNotEmpty()) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = LocalStrings.current.back,
                tint = Palette.TextPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onBrowse(parts.dropLast(1).joinToString("/")) }
                    .padding(8.dp)
                    .size(26.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        val crumbs = listOf(LocalStrings.current.allFiles to "") + parts.mapIndexed { i, name -> name to parts.take(i + 1).joinToString("/") }
        crumbs.forEachIndexed { i, (name, target) ->
            val last = i == crumbs.lastIndex
            if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(22.dp))
            Text(
                name,
                color = if (last) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 20.sp,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (last) Modifier else Modifier.clickable { onBrowse(target) })
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/** No files yet: show how to get some, step by step. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Palette.Blue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            FolderIcon(Palette.Blue, Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(s.noFilesYet, color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            s.emptySteps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Palette.Row), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Palette.Blue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(step, color = Palette.TextSecondary, fontSize = 19.sp)
                }
            }
        }
    }
}

/** Download link with the PIN in it, so a device can fetch the file straight from a QR code. */
private fun ServerState.downloadUrl(address: String, entry: FileEntry) =
    "http://$address:$port/api/download?path=${URLEncoder.encode(entry.path, "UTF-8").replace("+", "%20")}&k=$token"

@Composable
private fun SendToDeviceDialog(entry: FileEntry, url: String, onDismiss: () -> Unit) {
    AppDialog(
        title = LocalStrings.current.sendToDevice,
        onDismiss = onDismiss,
        confirm = { TextButton(onDismiss) { Text(LocalStrings.current.done, fontSize = 17.sp) } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QrCode(url, Modifier.size(240.dp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = Palette.TextPrimary, fontSize = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(formatSize(entry.size), color = Palette.TextSecondary, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    LocalStrings.current.sendToDeviceHint,
                    color = Palette.TextSecondary,
                    fontSize = 16.sp,
                )
            }
        }
    
    }
}

// ---------------------------------------------------------------- Messages

private val LINK = Regex("""(https?://\S+|www\.\S+)""", RegexOption.IGNORE_CASE)

/** The newest text from a device, as a one-line banner: open it as a link, look it up in maps, or copy it. */
@Composable
private fun MessageBanner(host: Host, message: TextMessage, more: Int) {
    val s = LocalStrings.current
    val time = remember(s.locale) { SimpleDateFormat("HH:mm", s.locale) }
    val link = remember(message.text) { LINK.find(message.text)?.value?.trimEnd('.', ',', ')', '!', '?') }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Blue.copy(alpha = 0.12f))
            .border(1.dp, Palette.Blue.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.fromDevice(time.format(Date(message.received))) + if (more > 0) " · " + s.moreMessages(more) else "",
                color = Palette.Blue,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(message.text, color = Palette.TextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(16.dp))
        if (link != null) MessageAction(s.openLink, primary = true) { host.openLink(link) }
        else MessageAction(s.navigate, primary = true) { host.navigate(message.text) }
        Spacer(Modifier.width(10.dp))
        MessageAction(s.copy) { host.copy(message.text) }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Outlined.Close,
            contentDescription = s.dismiss,
            tint = Palette.TextSecondary,
            modifier = Modifier.clip(CircleShape).clickable { host.dismiss(message) }.padding(12.dp).size(24.dp),
        )
    }
}

@Composable
private fun MessageAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
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

// ---------------------------------------------------------------- File rows

/** Files that arrived this recently get a short highlight. */
private const val FRESH_MS = 15_000L

@Composable
private fun FileRow(host: Host, entry: FileEntry, onSend: (() -> Unit)?) {
    val s = LocalStrings.current
    val dateFormat = remember(s.locale) { SimpleDateFormat("d MMM, HH:mm", s.locale) }
    var fresh by remember(entry.path, entry.modified) {
        mutableStateOf(!entry.isDirectory && System.currentTimeMillis() - entry.modified in 0..FRESH_MS)
    }
    LaunchedEffect(fresh) {
        if (fresh) {
            delay(2500)
            fresh = false
        }
    }
    val background by animateColorAsState(if (fresh) Palette.Blue.copy(alpha = 0.22f) else Palette.Row, tween(1200), label = "fresh")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable { host.open(entry) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(host, entry)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = Palette.TextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.isDirectory) s.folder else "${s.isolate(formatSize(entry.size))} · ${dateFormat.format(Date(entry.modified))}",
                color = Palette.TextSecondary,
                fontSize = 15.sp,
            )
        }
        if (onSend != null) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = s.sendToDevice,
                tint = Palette.TextSecondary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onSend).padding(12.dp).size(26.dp),
            )
        }
        DeleteButton { host.delete(entry) }
    }
}

/** Photo/video preview or APK icon when the app can make one, otherwise a coloured type badge. */
@Composable
private fun Thumbnail(host: Host, entry: FileEntry) {
    val px = with(LocalDensity.current) { 56.dp.roundToPx() }
    val image by produceState<ImageBitmap?>(null, entry.path, entry.modified) {
        value = runCatching { host.thumbnail(entry, px)?.asImageBitmap() }.getOrNull()
    }
    val shape = RoundedCornerShape(12.dp)
    image?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(shape))
    } ?: TypeBadge(entry)
}

/** Two-step delete: first tap arms it, second tap deletes. Disarms itself after 3s. */
@Composable
private fun DeleteButton(onDelete: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    if (armed) {
        Text(
            LocalStrings.current.delete,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Palette.Red)
                .clickable(onClick = onDelete)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    } else {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = LocalStrings.current.delete,
            tint = Palette.TextSecondary,
            modifier = Modifier.clip(CircleShape).clickable { armed = true }.padding(12.dp).size(26.dp),
        )
    }
}

@Composable
private fun TypeBadge(entry: FileEntry) {
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (entry.isDirectory) Color(0xFF1D2A44) else colorFor(ext)),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.isDirectory) {
            FolderIcon(Palette.Blue, Modifier.size(28.dp))
        } else {
            Text(
                ext.take(4).uppercase().ifEmpty { LocalStrings.current.fileBadge },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun colorFor(ext: String): Color = when (ext) {
    "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> Color(0xFF2FA8FF)
    "mp4", "mkv", "mov", "avi", "webm", "m4v" -> Color(0xFFA46BFF)
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus" -> Color(0xFFFF8A3D)
    "apk" -> Color(0xFF2FD27A)
    "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> Color(0xFFFF5A7A)
    "zip", "rar", "7z", "tar", "gz" -> Color(0xFFE0B030)
    else -> Color(0xFF3A3A3A)
}

// ---------------------------------------------------------------- Transfers

/** One slim line per upload: name, status, thin progress bar (bars line up at the end). Finished ones disappear after a few seconds. */
@Composable
private fun TransferRow(t: Transfer) {
    val s = LocalStrings.current
    val progress = if (t.total > 0) (t.received.toFloat() / t.total).coerceIn(0f, 1f) else 0f
    val percent = "${(progress * 100).toInt()}%"
    val (color, status) = when (t.status) {
        Transfer.Status.Active -> Palette.Blue to percent
        Transfer.Status.Paused -> Palette.TextSecondary to "$percent · ${s.waitingForDevice}"
        Transfer.Status.Done -> Palette.Green to "✓ ${s.received}"
        Transfer.Status.Failed -> Palette.Red to s.failed
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Row)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("↓", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(t.name, color = Palette.TextPrimary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(status, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(140.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Track)) {
            val fill = if (t.status == Transfer.Status.Active || t.status == Transfer.Status.Paused) progress else 1f
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (t.status == Transfer.Status.Active) Brush.horizontalGradient(listOf(Palette.Blue, Palette.Cyan)) else Brush.horizontalGradient(listOf(color, color))),
            )
        }
        Spacer(Modifier.width(16.dp))
    }
}
