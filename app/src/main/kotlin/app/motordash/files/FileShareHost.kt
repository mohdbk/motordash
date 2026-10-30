package app.motordash.files

import app.motordash.api.StorageLocation
import android.graphics.Bitmap
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import app.motordash.AppText
import app.motordash.api.FileEntry
import app.motordash.api.Folder
import app.motordash.api.Host
import app.motordash.api.ServerState
import app.motordash.api.TextMessage
import app.motordash.api.UpdateState
import app.motordash.update.Updater
import app.motordash.api.Transfer
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Runs the file server and exposes its state to the dashboard. */
class FileShareHost(private val context: Context) : Host, FileServer.Listener {

    // Everything the car and the devices see lives in this folder (see StorageFolders).
    private val folders = StorageFolders(context)
    private val _storage = mutableStateOf(folders.load())
    override val storage: State<StorageLocation> = _storage
    @Volatile private var root = folders.dirFor(_storage.value)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val strings = AppText(context)
    private val token = randomToken()
    private var fileServer: FileServer? = null

    private val _server = mutableStateOf(ServerState())
    private val _folder = mutableStateOf(Folder())
    private val _transfers = mutableStateOf<List<Transfer>>(emptyList())
    private val _messages = mutableStateOf<List<TextMessage>>(emptyList())
    private val _devices = mutableStateOf(0)
    override val server: State<ServerState> = _server
    override val folder: State<Folder> = _folder
    override val transfers: State<List<Transfer>> = _transfers
    override val messages: State<List<TextMessage>> = _messages
    override val devicesConnected: State<Int> = _devices
    /** Debug builds feed hot reload status and the server dialog in here (see debug DashboardHost). */
    val devStatusState = mutableStateOf<String?>(null)
    var onOpenDevSettings: () -> Unit = {}
    override val devStatus: State<String?> = devStatusState

    private val updater = Updater(context, scope)
    override val update: State<UpdateState> = updater.state

    private val active = ConcurrentHashMap<Long, Transfer>()
    private val nextMessageId = AtomicLong()
    private val lastSeen = ConcurrentHashMap<String, Long>()
    private val thumbnails = Thumbnails(context)

    fun start() {
        if (fileServer != null) return
        root.mkdirs()
        scope.launch(Dispatchers.IO) { deleteStaleParts() }
        updater.start()
        var error: String? = null
        for (port in PORTS) {
            try {
                val server = FileServer(port, root, token = token, home = "", pageHtml = ::pageHtml, listener = this, asset = ::readAsset)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                fileServer = server
                _server.value = ServerState(running = true, port = port, token = token)
                break
            } catch (e: IOException) {
                error = e.message
            }
        }
        if (fileServer == null) _server.value = ServerState(error = strings.serverFailed(error))

        // Wi-Fi/hotspot can come and go while the app is open.
        scope.launch {
            while (true) {
                val addresses = withContext(Dispatchers.IO) { lanAddresses() }
                if (addresses != _server.value.addresses) _server.value = _server.value.copy(addresses = addresses)
                forgetQuietDevices()
                delay(3000)
            }
        }
        browse("")
    }

    // ---------------------------------------------------------------- Host

    override fun browse(path: String) {
        scope.launch {
            _folder.value = withContext(Dispatchers.IO) { readFolder(path) }
        }
    }

    override fun open(entry: FileEntry) {
        if (entry.isDirectory) return browse(entry.path)
        val file = File(root, entry.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeTypeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, strings.cantOpen(file.name), Toast.LENGTH_LONG).show()
        }
    }

    override fun openDevSettings() = onOpenDevSettings()

    override fun changeStorage(base: StorageLocation.Base, name: String, moveExisting: Boolean) {
        val target = StorageLocation(base, name.trim())
        if (!StorageLocation.isValidName(target.name)) return toast(strings.badFolderName())
        if (target.path == _storage.value.path) return
        if (active.values.any { it.status == Transfer.Status.Active || it.status == Transfer.Status.Paused }) {
            return toast(strings.waitForUploads())
        }
        scope.launch {
            val old = root
            val new = folders.dirFor(target)
            val ok = withContext(Dispatchers.IO) {
                new.mkdirs()
                if (moveExisting) moveContents(old, new) else true
            }
            if (!new.isDirectory) return@launch toast(strings.cantCreateFolder(target.path))
            if (!ok) toast(strings.someNotMoved())
            root = new
            fileServer?.root = new
            folders.save(target)
            _storage.value = target
            browse("")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun checkForUpdate() = updater.check()

    override fun installUpdate() = updater.install()

    override fun dismiss(message: TextMessage) {
        _messages.value = _messages.value - message
    }

    override fun openLink(url: String) {
        val uri = Uri.parse(if (url.contains("://")) url else "https://$url")
        startOrToast(Intent(Intent.ACTION_VIEW, uri), strings.noBrowser())
    }

    override fun navigate(query: String) {
        startOrToast(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))), strings.noMaps())
    }

    override fun copy(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("MotorDash", text))
        Toast.makeText(context, strings.copied(), Toast.LENGTH_SHORT).show()
    }

    private fun startOrToast(intent: Intent, error: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    override fun delete(entry: FileEntry) {
        scope.launch(Dispatchers.IO) {
            File(root, entry.path).deleteRecursively()
            onFilesChanged()
        }
    }

    // ---------------------------------------------------------------- FileServer.Listener (server threads)

    override fun onUploadStarted(id: Long, name: String, total: Long) {
        active[id] = Transfer(id, name, 0, total, Transfer.Status.Active)
        publishTransfers()
    }

    override fun onUploadProgress(id: Long, received: Long) {
        active.computeIfPresent(id) { _, t -> t.copy(received = received, status = Transfer.Status.Active) }
        publishTransfers()
    }

    override fun onUploadPaused(id: Long) {
        active.computeIfPresent(id) { _, t -> t.copy(status = Transfer.Status.Paused) }
        publishTransfers()
        // The device gave up (or left) if nothing happened for a while; the .part is cleaned up on a later start.
        scope.launch {
            delay(PAUSED_TIMEOUT_MS)
            if (active[id]?.status == Transfer.Status.Paused) {
                active.remove(id)
                publishTransfers()
            }
        }
    }

    override fun onUploadFinished(id: Long, file: File?) {
        active.computeIfPresent(id) { _, t ->
            if (file != null) t.copy(received = t.total, status = Transfer.Status.Done)
            else t.copy(status = Transfer.Status.Failed)
        }
        publishTransfers()
        // Make received media show up in the car's own gallery/music apps.
        file?.let { MediaScannerConnection.scanFile(context, arrayOf(it.path), null, null) }
        scope.launch {
            delay(5000)
            active.remove(id)
            publishTransfers()
        }
    }

    override fun onFilesChanged() {
        browse(_folder.value.path)
    }

    override fun onDeviceSeen(address: String) {
        if (lastSeen.put(address, System.currentTimeMillis()) == null) scope.launch { _devices.value = lastSeen.size }
    }

    /** The device page pings every 15 s while open; quiet for longer means it closed or left. */
    private fun forgetQuietDevices() {
        val cutoff = System.currentTimeMillis() - DEVICE_TIMEOUT_MS
        lastSeen.entries.removeIf { it.value < cutoff }
        _devices.value = lastSeen.size
    }

    override suspend fun thumbnail(entry: FileEntry, sizePx: Int): Bitmap? =
        if (entry.isDirectory) null else thumbnails.get(File(root, entry.path), sizePx)

    override fun onTextReceived(text: String) {
        val message = TextMessage(nextMessageId.incrementAndGet(), text, System.currentTimeMillis())
        scope.launch { _messages.value = (listOf(message) + _messages.value).take(MAX_MESSAGES) }
    }

    // ---------------------------------------------------------------- helpers

    private fun pageHtml() = context.assets.open("web/index.html").use { it.readBytes() }

    private fun readAsset(path: String) = runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()

    /** Unfinished resumable uploads older than a day won't be resumed. */
    private fun deleteStaleParts() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        root.walkTopDown()
            .filter { it.isFile && it.name.startsWith(".") && it.name.endsWith(FileServer.PART) && it.lastModified() < cutoff }
            .forEach { it.delete() }
    }

    private fun publishTransfers() {
        val snapshot = active.values.sortedBy { it.id }
        scope.launch { _transfers.value = snapshot }
    }

    private fun readFolder(path: String): Folder {
        val dir = File(root, path).takeIf { it.isDirectory } ?: root
        val relativePath = dir.toRelativeString(root)
        val entries = dir.listFiles().orEmpty()
            .filterNot { it.name.endsWith(".part") }
            .sortedWith(FileServer.FILE_ORDER)
            .map {
                FileEntry(
                    path = if (relativePath.isEmpty()) it.name else "$relativePath/${it.name}",
                    name = it.name,
                    isDirectory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0,
                    modified = it.lastModified(),
                )
            }
        if (relativePath.isEmpty()) {
            // Download/ or Documents/ itself also holds other apps' folders: never offer to move those.
            val count = if (_storage.value.name.isEmpty()) 0 else entries.size
            scope.launch { if (_storage.value.itemCount != count) _storage.value = _storage.value.copy(itemCount = count) }
        }
        return Folder(relativePath, "", entries, root.usableSpace)
    }

    private companion object {
        val PORTS = listOf(8765, 8766, 8767, 8780)
        const val MAX_MESSAGES = 20
        const val PAUSED_TIMEOUT_MS = 10 * 60 * 1000L
        const val DEVICE_TIMEOUT_MS = 45_000L

        /** 6-digit PIN: in the QR code, and typed on the device when it can't scan. */
        fun randomToken(): String = "%06d".format(SecureRandom().nextInt(1_000_000))
    }
}

internal fun mimeTypeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
