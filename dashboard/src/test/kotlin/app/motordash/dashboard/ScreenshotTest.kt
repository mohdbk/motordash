package app.motordash.dashboard

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.takahirom.roborazzi.captureRoboImage
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import app.motordash.api.FileEntry
import app.motordash.api.Folder
import app.motordash.api.Host
import app.motordash.api.ServerState
import app.motordash.api.TextMessage
import app.motordash.api.StorageLocation
import app.motordash.api.Transfer
import app.motordash.api.UpdateState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** Renders the dashboard at a DiLink-like 1920x1080 (1280x720dp @ 1.5x) into build/screenshots/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1280dp-h720dp-land-hdpi")
class ScreenshotTest {

    @Test
    fun filesPage() = capture("files", FakeHost())

    @Test
    fun filesPageEmpty() = capture("files-empty", FakeHost(entries = emptyList(), transfers = emptyList(), messages = emptyList()))

    @Test
    fun settingsPage() = capture("settings", FakeHost(), page = Page.Settings)

    @Test
    fun technicalPage() = capture("technical", FakeHost(), page = Page.Technical)

    @Test
    fun filesPageArabic() = capture("files-ar", FakeHost(), language = Language.Arabic)

    @Test
    fun settingsPageArabic() = capture("settings-ar", FakeHost(), page = Page.Settings, language = Language.Arabic)

    @Test
    @Config(qualifiers = "w720dp-h1280dp-port-hdpi")
    fun filesPagePortrait() = capture("files-portrait", FakeHost())

    private fun capture(name: String, host: Host, page: Page = Page.Files, language: Language = Language.English) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(android.R.style.Theme_Material_NoActionBar) // as in the app
        val activity = controller.setup().get()
        activity.setContent { Dashboard(host, initialPage = page, language = language) }
        ShadowLooper.idleMainLooper()
        activity.window.decorView.captureRoboImage("build/screenshots/$name.png")
    }
}

private class FakeHost(
    entries: List<FileEntry> = listOf(
        FileEntry("Music", "Music", true, 0, 0),
        FileEntry("road-trip.mp4", "road-trip.mp4", false, 734_003_200, 1_793_380_000_000),
        FileEntry("IMG_2041.jpg", "IMG_2041.jpg", false, 3_481_000, 1_793_381_000_000),
        FileEntry("podcast.mp3", "podcast.mp3", false, 58_200_000, 1_793_382_000_000),
        FileEntry("MotorDash-0.2.apk", "MotorDash-0.2.apk", false, 9_739_432, 1_793_383_000_000),
    ),
    transfers: List<Transfer> = listOf(
        Transfer(1, "holiday-video.mov", 420_000_000, 1_000_000_000, Transfer.Status.Active),
        Transfer(2, "IMG_2042.jpg", 3_000_000, 3_000_000, Transfer.Status.Done),
        Transfer(3, "dashcam-0412.mp4", 1_200_000_000, 2_000_000_000, Transfer.Status.Paused),
    ),
    messages: List<TextMessage> = listOf(TextMessage(1, "Alexanderplatz 1, 10178 Berlin", 1_793_383_500_000)),
) : Host {
    override val server: State<ServerState> = mutableStateOf(
        ServerState(running = true, addresses = listOf("192.168.43.128", "10.0.0.5"), port = 8765, token = "482913"),
    )
    override val folder: State<Folder> = mutableStateOf(
        Folder(entries = entries, freeBytes = 38_200_000_000),
    )
    override val transfers: State<List<Transfer>> = mutableStateOf(transfers)
    override val messages: State<List<TextMessage>> = mutableStateOf(messages)
    override val devicesConnected: State<Int> = mutableStateOf(1)
    override val storage: State<StorageLocation> = mutableStateOf(StorageLocation(itemCount = 5))
    override fun changeStorage(base: StorageLocation.Base, name: String, moveExisting: Boolean) {}
    // Stand-in previews: a gradient "photo" and a darker "video frame".
    override suspend fun thumbnail(entry: FileEntry, sizePx: Int): Bitmap? {
        val colors = when (entry.name.substringAfterLast('.')) {
            "jpg" -> intArrayOf(0xFFFFB45C.toInt(), 0xFF3D8BFF.toInt())
            "mp4" -> intArrayOf(0xFF1E2A44.toInt(), 0xFFA46BFF.toInt())
            else -> return null
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP) }
            Canvas(it).drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
        }
    }
    override val devStatus: State<String?> = mutableStateOf("v1793382115224000000")
    override val update: State<UpdateState> = mutableStateOf(
        UpdateState(UpdateState.Status.Available, currentVersion = "0.1.0", latestVersion = "0.2.0", notes = "Send text and links to the car. Uploads resume after a dropped connection."),
    )
    override fun checkForUpdate() {}
    override fun installUpdate() {}
    override fun browse(path: String) {}
    override fun open(entry: FileEntry) {}
    override fun delete(entry: FileEntry) {}
    override fun openDevSettings() {}
    override fun dismiss(message: TextMessage) {}
    override fun openLink(url: String) {}
    override fun navigate(query: String) {}
    override fun copy(text: String) {}
}
