package app.motordash.update

import fi.iki.elonen.NanoHTTPD
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random

class UpdateClientTest {
    private val apk = Random(1).nextBytes(300_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private var manifest = ""
    private lateinit var server: NanoHTTPD
    private lateinit var base: String
    private lateinit var dir: File

    @Before
    fun setUp() {
        val port = ServerSocket(0).use { it.localPort }
        server = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response = when (session.uri) {
                "/releases/latest/update.json" -> newFixedLengthResponse(Response.Status.OK, "application/json", manifest)
                "/releases/latest/MotorDash-0.2.0.apk" ->
                    newFixedLengthResponse(Response.Status.OK, "application/octet-stream", apk.inputStream(), apk.size.toLong())
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "nope")
            }
        }.apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        base = "http://127.0.0.1:$port"
        dir = Files.createTempDirectory("updates").toFile()
        manifest = """{"versionCode": 2, "versionName": "0.2.0", "apk": "MotorDash-0.2.0.apk", "sha256": "$sha", "notes": "New stuff"}"""
    }

    @After
    fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    @Test
    fun `reads the manifest and resolves the apk next to it`() {
        val release = UpdateClient("$base/releases/latest/update.json").fetchLatest()
        assertEquals(2, release.versionCode)
        assertEquals("0.2.0", release.versionName)
        assertEquals("$base/releases/latest/MotorDash-0.2.0.apk", release.apkUrl)
        assertEquals("New stuff", release.notes)
    }

    @Test
    fun `absolute apk urls and empty notes`() {
        val release = UpdateClient.parse(
            """{"versionCode": 3, "versionName": "1.0", "apk": "https://cdn.example/x.apk", "sha256": "ab", "notes": ""}""",
            "https://github.com/u/r/releases/latest/download/update.json",
        )
        assertEquals("https://cdn.example/x.apk", release.apkUrl)
        assertNull(release.notes)
    }

    @Test
    fun `download verifies the checksum and reports progress`() {
        val client = UpdateClient("$base/releases/latest/update.json")
        val dest = File(dir, "MotorDash-2.apk")
        var last = 0f
        client.download(client.fetchLatest(), dest) { last = it }
        assertArrayEquals(apk, dest.readBytes())
        assertEquals(1f, last)
    }

    @Test
    fun `tampered download is rejected and leaves nothing behind`() {
        manifest = manifest.replace(sha, "0".repeat(64))
        val client = UpdateClient("$base/releases/latest/update.json")
        val dest = File(dir, "MotorDash-2.apk")
        val error = runCatching { client.download(client.fetchLatest(), dest) {} }.exceptionOrNull()
        assertTrue(error is IOException)
        assertFalse(dest.exists())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun `missing manifest is an error`() {
        val error = runCatching { UpdateClient("$base/nothing/update.json").fetchLatest() }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
