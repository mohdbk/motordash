package app.motordash.update

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches `update.json` (written by `./gradlew :app:releaseFiles`) and downloads the APK it
 * points to. Plain JVM so it can be tested without a device.
 *
 *   {"versionCode": 2, "versionName": "0.2.0", "apk": "MotorDash-0.2.0.apk", "sha256": "…", "notes": "…"}
 *
 * `apk` may be relative to the manifest URL, so both files can sit side by side anywhere,
 * e.g. as assets of the latest GitHub release.
 */
class UpdateClient(private val manifestUrl: String) {

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val notes: String?,
    )

    fun fetchLatest(): Release = parse(get(manifestUrl) { input, _ -> input.readBytes() }.decodeToString(), manifestUrl)

    /** Downloads to [dest] and checks the hash; a partial or tampered file never ends up at [dest]. */
    fun download(release: Release, dest: File, progress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val temp = File(dest.path + ".download")
        val digest = MessageDigest.getInstance("SHA-256")
        get(release.apkUrl) { input, length ->
            temp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    done += n
                    if (length > 0) progress((done.toFloat() / length).coerceAtMost(1f))
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(release.sha256, ignoreCase = true)) {
            temp.delete()
            throw IOException("Download is corrupted (checksum mismatch)")
        }
        if (!temp.renameTo(dest)) throw IOException("Could not save the update")
    }

    private fun <T> get(url: String, read: (InputStream, length: Long) -> T): T {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} from ${URL(url).host}")
            return conn.inputStream.use { read(it, conn.contentLengthLong) }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun parse(json: String, baseUrl: String): Release {
            val o = JSONObject(json)
            return Release(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = URL(URL(baseUrl), o.getString("apk")).toString(),
                sha256 = o.getString("sha256"),
                notes = o.optString("notes").takeIf { it.isNotBlank() },
            )
        }
    }
}
