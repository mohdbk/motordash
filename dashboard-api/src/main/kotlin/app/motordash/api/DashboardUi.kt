package app.motordash.api

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Bump whenever anything in this module changes. The dev server only pushes dashboard
 * payloads to apps built against the same version (devserver/serve.py reads this line).
 */
const val API_VERSION = 11

/**
 * Contract between the installed app shell and the hot-reloadable dashboard module.
 * Changing anything in this module requires reinstalling the APK.
 */
interface DashboardUi {
    @Composable
    fun Content(host: Host)
}

/** Everything the dashboard can see and do. Implemented by the app shell. */
interface Host {
    val server: State<ServerState>
    val folder: State<Folder>
    val transfers: State<List<Transfer>>
    /** Where received files are saved. */
    val storage: State<StorageLocation>
    /** Devices with the web page open right now (it sends a heartbeat). */
    val devicesConnected: State<Int>
    /** Text sent from a device, newest first. */
    val messages: State<List<TextMessage>>
    /** In-app updates; [UpdateState.Status.Disabled] when no update URL is configured (debug builds). */
    val update: State<UpdateState>
    /** Hot reload connection status in debug builds, null in release builds. */
    val devStatus: State<String?>

    /** Show the folder at [path], relative to Download/MotorDash ("" = top). */
    fun browse(path: String)
    /** Open a file with the system viewer (videos, music, APK installer, ...). */
    fun open(entry: FileEntry)
    fun delete(entry: FileEntry)
    /** A small preview (photos, videos, APK icons), or null for other files. Cached. */
    suspend fun thumbnail(entry: FileEntry, sizePx: Int): Bitmap?
    fun dismiss(message: TextMessage)
    /** Open a web link in the car's browser. */
    fun openLink(url: String)
    /** Search for an address or place in the car's maps app. */
    fun navigate(query: String)
    fun copy(text: String)
    /**
     * Saves received files to [base]/[name] from now on, moving what's already there if [moveExisting].
     * Refused (with a message on screen) while uploads are running or if the name isn't usable.
     */
    fun changeStorage(base: StorageLocation.Base, name: String, moveExisting: Boolean)
    fun checkForUpdate()
    /** Downloads the available update if needed and opens the system installer. */
    fun installUpdate()
    /** Debug builds: lets the user change the hot reload server URL and token. */
    fun openDevSettings()
}

data class ServerState(
    val running: Boolean = false,
    /** IPv4 addresses devices can reach, best guess first. */
    val addresses: List<String> = emptyList(),
    val port: Int = 0,
    val token: String = "",
    val error: String? = null,
) {
    fun url(address: String) = "http://$address:$port/?k=$token"
}

data class Folder(
    val path: String = "",
    /** Where browsing starts and the device's uploads land by default. */
    val home: String = "",
    val entries: List<FileEntry> = emptyList(),
    val freeBytes: Long = 0,
)

data class FileEntry(
    /** Relative to Download/MotorDash. */
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
)

data class Transfer(
    val id: Long,
    val name: String,
    val received: Long,
    val total: Long,
    val status: Status,
) {
    /** Paused: the connection dropped and the device will resume it. */
    enum class Status { Active, Paused, Done, Failed }
}

data class TextMessage(
    val id: Long,
    val text: String,
    val received: Long,
)

data class UpdateState(
    val status: Status = Status.Disabled,
    val currentVersion: String = "",
    val latestVersion: String? = null,
    val notes: String? = null,
    /** Download progress, 0..1. */
    val progress: Float = 0f,
    val error: String? = null,
    val lastChecked: Long = 0,
) {
    enum class Status { Disabled, Idle, Checking, UpToDate, Available, Downloading, ReadyToInstall, Failed }
}

data class StorageLocation(
    val base: Base = Base.Download,
    /** Empty = straight into [base], no subfolder. */
    val name: String = "MotorDash",
    /** Files and folders directly inside, to offer moving them; always 0 for a bare [base]. */
    val itemCount: Int = 0,
) {
    /** Android lets apps save any file type only in these two public folders. */
    enum class Base(val dirName: String) { Download("Download"), Documents("Documents") }

    val path: String get() = if (name.isEmpty()) base.dirName else "${base.dirName}/$name"

    companion object {
        /** Empty (use [base] itself), or a single folder name: no slashes, not "." or "..", not too long. */
        fun isValidName(name: String) =
            name.isEmpty() || name.isNotBlank() && name.length <= 64 && name.trim() !in setOf(".", "..") && name.none { it == '/' || it == '\\' || it < ' ' }
    }
}
