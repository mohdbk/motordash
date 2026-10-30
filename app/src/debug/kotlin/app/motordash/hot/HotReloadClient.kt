package app.motordash.hot

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.motordash.BuildConfig
import app.motordash.api.API_VERSION
import app.motordash.api.DashboardUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Long-polls the dev server (devserver/serve.py) and, whenever it has a new dashboard
 * payload, downloads the dex jar and loads it into a fresh classloader.
 */
class HotReloadClient(private val context: Context) {
    data class Config(val url: String, val token: String)

    private val prefs = context.getSharedPreferences("motordash-dev", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(
        Config(
            url = prefs.getString("url", null) ?: BuildConfig.DEV_SERVER_URL,
            token = prefs.getString("token", null) ?: BuildConfig.DEV_TOKEN,
        )
    )
    val config: StateFlow<Config> = _config

    var ui by mutableStateOf<DashboardUi?>(null)
        private set
    var status by mutableStateOf("starting")
        private set

    fun updateConfig(url: String, token: String) {
        prefs.edit().putString("url", url).putString("token", token).apply()
        _config.value = Config(url.trimEnd('/'), token)
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        _config.collectLatest { poll(it) }
    }

    private suspend fun poll(config: Config) {
        if (config.url.isBlank()) {
            status = "no server set (tap)"
            awaitCancellation()
        }
        var loaded = 0L
        while (true) {
            try {
                status = if (loaded == 0L) "connecting" else "v$loaded"
                val version = request(config, "/wait?since=$loaded&api=$API_VERSION").decodeToString().trim().toLong()
                if (version != 0L && version != loaded) {
                    status = "loading…"
                    ui = load(request(config, "/payload?v=$version&api=$API_VERSION"), version)
                    loaded = version
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                status = "error: ${e.message ?: e.javaClass.simpleName}"
                delay(3000)
            }
        }
    }

    private fun request(config: Config, path: String): ByteArray {
        val conn = URL(config.url.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 40_000 // server holds /wait for up to 25s
            conn.setRequestProperty("X-MotorDash-Token", config.token)
            // Skip the interstitial pages some tunnel providers inject.
            conn.setRequestProperty("ngrok-skip-browser-warning", "1")
            conn.setRequestProperty("X-Pinggy-No-Screen", "1")
            val code = conn.responseCode
            if (code == 409) throw IllegalStateException("APK outdated, reinstall")
            if (code != 200) throw IllegalStateException("HTTP $code")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun load(bytes: ByteArray, version: Long): DashboardUi {
        val dir = File(context.codeCacheDir, "hot").apply { mkdirs() }
        // Already-loaded payloads stay mapped in memory, so old files can go.
        dir.listFiles()?.forEach { it.delete() }
        val jar = File(dir, "payload-$version.jar")
        jar.writeBytes(bytes)
        jar.setReadOnly() // required for dynamically loaded code on Android 14+
        val loader = ChildFirstDexClassLoader(jar.path, dir.path, context.classLoader)
        val cls = loader.loadClass("app.motordash.dashboard.DashboardUiImpl")
        return cls.getDeclaredConstructor().newInstance() as DashboardUi
    }
}
