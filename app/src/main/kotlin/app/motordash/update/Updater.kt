package app.motordash.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import app.motordash.AppText
import app.motordash.BuildConfig
import app.motordash.api.UpdateState
import app.motordash.api.UpdateState.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Checks for a newer release now and then and, when asked, downloads it and opens Android's
 * installer. Sideloaded apps can't install silently, so the user still confirms once.
 * Disabled when no update URL is configured (always in debug builds).
 */
class Updater(private val context: Context, private val scope: CoroutineScope) {
    private val client = BuildConfig.UPDATE_URL.takeIf { it.isNotBlank() }?.let(::UpdateClient)
    private val dir = File(context.cacheDir, "updates")
    private val strings = AppText(context)
    private var latest: UpdateClient.Release? = null
    private var job: Job? = null

    private val _state = mutableStateOf(
        UpdateState(status = if (client == null) Status.Disabled else Status.Idle, currentVersion = BuildConfig.VERSION_NAME)
    )
    val state: State<UpdateState> = _state

    fun start() {
        if (client == null) return
        scope.launch {
            delay(STARTUP_DELAY_MS) // let Wi-Fi/hotspot come up first
            while (true) {
                check()
                delay(CHECK_EVERY_MS)
            }
        }
    }

    fun check() {
        val client = client ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            update { it.copy(status = Status.Checking, error = null) }
            try {
                val release = withContext(Dispatchers.IO) { client.fetchLatest() }
                latest = release
                val newer = release.versionCode > BuildConfig.VERSION_CODE
                if (!newer) dir.deleteRecursively() // installed already; drop the old download
                update {
                    it.copy(
                        status = when {
                            !newer -> Status.UpToDate
                            apkFor(release).isFile -> Status.ReadyToInstall
                            else -> Status.Available
                        },
                        latestVersion = release.versionName,
                        notes = release.notes,
                        lastChecked = System.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                update { it.copy(status = Status.Failed, error = strings.checkFailed(e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    /** Downloads the update if needed, then hands it to the system installer. */
    fun install() {
        val client = client ?: return
        val release = latest ?: return check()
        if (job?.isActive == true) return
        job = scope.launch {
            val apk = apkFor(release)
            try {
                if (!apk.isFile) {
                    update { it.copy(status = Status.Downloading, progress = 0f, error = null) }
                    withContext(Dispatchers.IO) {
                        dir.listFiles()?.forEach { it.delete() }
                        client.download(release, apk) { p -> scope.launch { update { it.copy(progress = p) } } }
                    }
                }
                if (!signedLikeUs(apk)) {
                    apk.delete()
                    throw SecurityException(strings.notOurKey())
                }
                update { it.copy(status = Status.ReadyToInstall, progress = 1f) }
                promptInstall(apk)
            } catch (e: Exception) {
                update { it.copy(status = Status.Failed, error = strings.updateFailed(e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    private fun promptInstall(apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            // One-time: the user lets MotorDash open the installer ("Install unknown apps").
            Toast.makeText(context, strings.allowInstall(), Toast.LENGTH_LONG).show()
            start(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        start(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, strings.noInstaller(), Toast.LENGTH_LONG).show()
        }
    }

    /** Android would refuse a differently signed update anyway; this gives a clear message instead. */
    @Suppress("DEPRECATION")
    private fun signedLikeUs(apk: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val theirs = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        val ours = pm.getPackageInfo(context.packageName, flags)
        return theirs.packageName == context.packageName && signatures(theirs) == signatures(ours)
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return sigs.orEmpty().map { it.toCharsString() }.toSet()
    }

    private fun apkFor(release: UpdateClient.Release) = File(dir, "MotorDash-${release.versionCode}.apk")

    private fun update(change: (UpdateState) -> UpdateState) {
        _state.value = change(_state.value)
    }

    private companion object {
        const val STARTUP_DELAY_MS = 30_000L
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    }
}
