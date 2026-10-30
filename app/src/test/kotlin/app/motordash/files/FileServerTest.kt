package app.motordash.files

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import kotlin.random.Random

class FileServerTest {
    private lateinit var root: File
    private lateinit var server: FileServer
    private lateinit var base: String
    private val events = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val token = "123456"
    private val cookie = "motordash=$token"

    @Before
    fun setUp() {
        root = Files.createTempDirectory("motordash").toFile()
        val port = ServerSocket(0).use { it.localPort }
        File(root, "Download/MotorDash").mkdirs()
        server = FileServer(port, root, home = "", token = token, pageHtml = { "<html>MotorDash</html>".toByteArray() }, asset = { if (it == "fonts/Dubai-Regular.woff2") byteArrayOf(1, 2, 3) else null }, listener = object : FileServer.Listener {
            override fun onUploadStarted(id: Long, name: String, total: Long) { events += "start:$name:$total" }
            override fun onUploadProgress(id: Long, received: Long) { events += "progress" }
            override fun onUploadFinished(id: Long, file: File?) { events += "finish:${file?.name}" }
            override fun onFilesChanged() {}
            override fun onUploadPaused(id: Long) { events += "paused" }
            override fun onDeviceSeen(address: String) { events += "seen:$address" }
            override fun onTextReceived(text: String) { events += "text:$text" }
        })
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        base = "http://127.0.0.1:$port"
    }

    @After
    fun tearDown() {
        server.stop()
        root.deleteRecursively()
    }

    @Test
    fun `token in QR url sets auth cookie, everything else requires it`() {
        val page = request("GET", "/?k=$token", withCookie = false)
        assertEquals(200, page.code)
        assertTrue(page.headers["Set-Cookie"].orEmpty().any { it.startsWith(cookie) })

        assertEquals(401, request("GET", "/?k=654321", withCookie = false).code)
        assertTrue(String(request("GET", "/", withCookie = false).body).contains("Enter the PIN"))
        assertEquals(401, request("GET", "/api/list?path=", withCookie = false).code)
        assertEquals(401, request("PUT", "/api/upload?path=&name=x.txt", body = "hi".toByteArray(), withCookie = false).code)
        assertTrue(!File(root, "x.txt").exists())
    }

    @Test
    fun `resumable upload continues from what arrived`() {
        val data = Random(7).nextBytes(3_000_000)
        val q = "/api/upload?path=&name=${enc("long drive.mp4")}&id=abcdef123456&total=${data.size}"
        fun received() = JSONObject(String(request("GET", "/api/upload/status?path=&id=abcdef123456").body)).getLong("received")

        assertEquals(0, received())
        val first = request("PUT", "$q&offset=0", body = data.copyOfRange(0, 1_000_000))
        assertEquals(200, first.code)
        assertEquals(1_000_000, received())
        // Not visible as a file until it's complete.
        assertTrue("long drive" !in String(request("GET", "/api/list?path=").body))

        // A stale offset is refused and told where to continue.
        val stale = request("PUT", "$q&offset=0", body = data.copyOfRange(0, 10))
        assertEquals(409, stale.code)
        assertEquals(1_000_000, JSONObject(String(stale.body)).getLong("received"))

        val rest = request("PUT", "$q&offset=1000000", body = data.copyOfRange(1_000_000, data.size))
        assertEquals("long drive.mp4", JSONObject(String(rest.body)).getString("name"))
        assertArrayEquals(data, File(root, "long drive.mp4").readBytes())
        assertEquals(0, received())
        assertTrue(root.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun `dropped connection keeps the bytes for resuming`() {
        val data = Random(8).nextBytes(2_000_000)
        val path = "/api/upload?path=&name=clip.mp4&id=dropped0001&offset=0&total=${data.size}"
        Socket("127.0.0.1", base.substringAfterLast(':').toInt()).use { socket ->
            val out = socket.getOutputStream()
            out.write("PUT $path HTTP/1.1\r\nHost: x\r\nCookie: $cookie\r\nContent-Length: ${data.size}\r\n\r\n".toByteArray())
            out.write(data, 0, 700_000)
            out.flush()
            Thread.sleep(300)
        } // device loses Wi-Fi

        var received = 0L
        repeat(50) {
            received = JSONObject(String(request("GET", "/api/upload/status?path=&id=dropped0001").body)).getLong("received")
            if ("paused" in events) return@repeat
            Thread.sleep(100)
        }
        assertTrue("paused" in events)
        assertTrue(received in 1..700_000)

        val rest = "/api/upload?path=&name=clip.mp4&id=dropped0001&offset=$received&total=${data.size}"
        assertEquals(200, request("PUT", rest, body = data.copyOfRange(received.toInt(), data.size)).code)
        assertArrayEquals(data, File(root, "clip.mp4").readBytes())
    }

    @Test
    fun `text from the device reaches the listener`() {
        assertEquals(200, request("POST", "/api/text", body = "  Main St 12, Berlin \n".toByteArray()).code)
        assertTrue("text:Main St 12, Berlin" in events)
        assertEquals(400, request("POST", "/api/text", body = "   ".toByteArray()).code)
        assertEquals(401, request("POST", "/api/text", body = "hi".toByteArray(), withCookie = false).code)
    }

    @Test
    fun `download QR code works without a cookie but needs the PIN`() {
        File(root, "song.mp3").writeText("la")
        val ok = request("GET", "/api/download?path=song.mp3&k=$token", withCookie = false)
        assertEquals(200, ok.code)
        assertEquals("la", String(ok.body))
        assertEquals(401, request("GET", "/api/download?path=song.mp3&k=000000", withCookie = false).code)
        assertEquals(401, request("GET", "/api/download?path=song.mp3", withCookie = false).code)
    }

    @Test
    fun `PIN page follows the device's language`() {
        val ar = request("GET", "/", withCookie = false, headers = mapOf("Accept-Language" to "ar-AE,ar;q=0.9,en;q=0.8"))
        assertTrue(String(ar.body).contains("dir=\"rtl\""))
        assertTrue(String(ar.body).contains("أدخل الرمز السري"))
        val en = request("GET", "/", withCookie = false, headers = mapOf("Accept-Language" to "en-GB,ar;q=0.5"))
        assertTrue(String(en.body).contains("dir=\"ltr\""))
    }

    @Test
    fun `fonts are served without auth, nothing else is`() {
        assertEquals(200, request("GET", "/fonts/Dubai-Regular.woff2", withCookie = false).code)
        assertEquals(404, request("GET", "/fonts/secret.txt", withCookie = false).code)
        assertEquals(404, request("GET", "/fonts/..%2Fweb%2Findex.html", withCookie = false).code)
    }

    @Test
    fun `heartbeat marks the device as seen, but only when signed in`() {
        assertEquals(401, request("GET", "/api/ping", withCookie = false).code)
        assertTrue(events.none { it.startsWith("seen:") })
        assertEquals(200, request("GET", "/api/ping").code)
        assertTrue("seen:127.0.0.1" in events)
    }

    @Test
    fun `typed PIN may contain spaces`() {
        assertEquals(200, request("GET", "/?k=123%20456", withCookie = false).code)
    }

    @Test
    fun `repeated wrong PINs lock out even the right one`() {
        repeat(5) { assertEquals(401, request("GET", "/?k=000000", withCookie = false).code) }
        assertEquals(429, request("GET", "/?k=$token", withCookie = false).code)
        assertEquals(200, request("GET", "/", withCookie = true).code) // signed-in devices keep working
    }

    @Test
    fun `large upload streams to disk, lists, downloads and deletes`() {
        val data = Random(1).nextBytes(5 * 1024 * 1024 + 123)
        val up = request("PUT", "/api/upload?path=&name=${enc("road trip.mp4")}", body = data)
        assertEquals(200, up.code)
        assertArrayEquals(data, File(root, "road trip.mp4").readBytes())
        assertEquals("start:road trip.mp4:${data.size}", events.first { !it.startsWith("seen:") })
        assertEquals("finish:road trip.mp4", events.last())
        assertTrue(events.count { it == "progress" } >= 4)

        val list = JSONObject(request("GET", "/api/list?path=").text)
        val entry = list.getJSONArray("entries").getJSONObject(1) // after the Download folder
        assertEquals("road trip.mp4", entry.getString("name"))
        assertEquals(data.size.toLong(), entry.getLong("size"))

        val down = request("GET", "/api/download?path=${enc("road trip.mp4")}")
        assertEquals(200, down.code)
        assertArrayEquals(data, down.body)
        assertTrue(down.headers["Content-Disposition"]!!.first().contains("road%20trip.mp4"))

        assertEquals(200, request("DELETE", "/api/delete?path=${enc("road trip.mp4")}").code)
        assertTrue(!File(root, "road trip.mp4").exists())
    }

    @Test
    fun `same name gets a numbered copy instead of overwriting`() {
        request("PUT", "/api/upload?path=&name=song.mp3", body = "one".toByteArray())
        request("PUT", "/api/upload?path=&name=song.mp3", body = "two".toByteArray())
        assertEquals("one", File(root, "song.mp3").readText())
        assertEquals("two", File(root, "song (1).mp3").readText())
    }

    @Test
    fun `folders can be created and uploaded into`() {
        assertEquals(200, request("POST", "/api/mkdir?path=&name=Music").code)
        assertEquals(200, request("PUT", "/api/upload?path=Music&name=a.mp3", body = "x".toByteArray()).code)
        assertEquals("x", File(root, "Music/a.mp3").readText())
        val list = JSONObject(request("GET", "/api/list?path=Music").text)
        assertEquals("Music", list.getString("path"))
        assertEquals(1, list.getJSONArray("entries").length())
    }

    @Test
    fun `listing without a path starts in home, explicit empty path is the root`() {
        server.home = "Download/MotorDash"
        val home = JSONObject(request("GET", "/api/list").text)
        assertEquals("Download/MotorDash", home.getString("path"))
        val top = JSONObject(request("GET", "/api/list?path=").text)
        assertEquals("", top.getString("path"))
        assertEquals("Download", top.getJSONArray("entries").getJSONObject(0).getString("name"))
    }

    @Test
    fun `system Android folder at the top level cannot be deleted`() {
        File(root, "Android/data").mkdirs()
        assertEquals(400, request("DELETE", "/api/delete?path=Android").code)
        assertTrue(File(root, "Android/data").exists())
    }

    @Test
    fun `paths cannot escape the root`() {
        assertEquals(400, request("GET", "/api/list?path=${enc("../")}").code)
        assertEquals(400, request("GET", "/api/download?path=${enc("../../etc/passwd")}").code)
        assertEquals(400, request("PUT", "/api/upload?path=${enc("..")}&name=x", body = "x".toByteArray()).code)
        assertEquals(400, request("PUT", "/api/upload?path=&name=${enc("..")}", body = "x".toByteArray()).code)
        assertEquals(400, request("DELETE", "/api/delete?path=").code)
        assertTrue(root.exists())
        // Slashes in names are neutralised rather than creating paths.
        assertEquals(200, request("PUT", "/api/upload?path=&name=${enc("../evil.txt")}", body = "x".toByteArray()).code)
        assertTrue(File(root, ".._evil.txt").exists())
    }

    // ---------------------------------------------------------------- helpers

    private class Reply(val code: Int, val body: ByteArray, val headers: Map<String, List<String>>) {
        val text get() = body.decodeToString()
    }

    private fun request(method: String, path: String, body: ByteArray? = null, withCookie: Boolean = true, headers: Map<String, String> = emptyMap()): Reply {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        if (withCookie) conn.setRequestProperty("Cookie", cookie)
        headers.forEach(conn::setRequestProperty)
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code < 400) conn.inputStream else conn.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        return Reply(code, bytes, conn.headerFields.filterKeys { it != null }).also { conn.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
