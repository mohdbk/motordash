package app.motordash.files

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLConnection
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * HTTP API the device's browser talks to. Every path is resolved inside [root].
 *
 *   GET    /?k=PIN                    web UI (sets the auth cookie); no valid cookie = PIN form
 *   GET    /api/list[?path=]          folder listing; no path = home
 *   PUT    /api/upload?path=&name=    raw file body, streamed to disk
 *   PUT    /api/upload?path=&name=&id=&offset=&total=   resumable: appends from offset, finishes at total
 *   GET    /api/upload/status?path=&id=                 bytes of a resumable upload received so far
 *   GET    /api/download?path=[&k=PIN]  with k: a car-screen QR code, no cookie needed
 *   POST   /api/text                  text/plain body, shown on the car screen
 *   GET    /fonts/Dubai-<Weight>.woff2   web font for the device pages (no auth: nothing secret)
 *   GET    /api/ping                  heartbeat from an open device page ("device connected")
 *   POST   /api/mkdir?path=&name=
 *   DELETE /api/delete?path=
 */
class FileServer(
    port: Int,
    @Volatile var root: File,
    /** Folder (relative to [root]) a listing without a path starts in. */
    @Volatile var home: String,
    private val token: String,
    private val pageHtml: () -> ByteArray,
    private val listener: Listener,
    /** Reads a file from the app's assets (null if missing); used for the Dubai web fonts. */
    private val asset: (String) -> ByteArray? = { null },
) : NanoHTTPD(port) {

    interface Listener {
        fun onUploadStarted(id: Long, name: String, total: Long)
        fun onUploadProgress(id: Long, received: Long)
        fun onUploadFinished(id: Long, file: File?)
        /** A resumable upload was interrupted; the device may come back and continue it. */
        fun onUploadPaused(id: Long)
        fun onFilesChanged()
        fun onTextReceived(text: String)
        /** Any signed-in request; [address] is the device's IP. */
        fun onDeviceSeen(address: String)
    }

    private val nextId = AtomicLong()
    private val activeUploads = ConcurrentHashMap.newKeySet<String>()
    /** Resumable upload id -> transfer id, so a resumed upload continues the same row on the car. */
    private val transferIds = ConcurrentHashMap<String, Long>()
    private var failures = 0
    private var lockedUntil = 0L

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri.startsWith("/fonts/")) return font(uri.removePrefix("/fonts/"))
        val arabic = prefersArabic(session)
        // A PIN in the URL (QR codes, the PIN form) signs the device in; otherwise the cookie must.
        val pin = param(session, "k")
        if (pin != null) {
            checkPin(pin)?.let { (status, error) ->
                val message = error.message(arabic)
                return if (uri == "/") pinPage(status, message, arabic) else text(status, message).closing()
            }
        } else if (session.cookies.read(COOKIE)?.let(::tokenMatches) != true) {
            if (uri == "/") return pinPage(Response.Status.UNAUTHORIZED, null, arabic)
            return text(Response.Status.UNAUTHORIZED, "Scan the QR code on the car screen again.").closing()
        }
        listener.onDeviceSeen(session.remoteIpAddress)
        val response = try {
            when {
                uri == "/api/ping" -> text(Response.Status.OK, "ok")
                uri == "/" -> page()
                uri == "/api/list" && session.method == Method.GET -> list(session)
                uri == "/api/upload" && session.method == Method.PUT ->
                    if (param(session, "id") != null) resumableUpload(session) else upload(session)
                uri == "/api/upload/status" && session.method == Method.GET -> uploadStatus(session)
                uri == "/api/download" && session.method == Method.GET -> download(session)
                uri == "/api/mkdir" && session.method == Method.POST -> mkdir(session)
                uri == "/api/delete" && session.method == Method.DELETE -> delete(session)
                uri == "/api/text" && session.method == Method.POST -> receiveText(session)
                else -> text(Response.Status.NOT_FOUND, "Not found").closing()
            }
        } catch (e: BadRequest) {
            text(Response.Status.BAD_REQUEST, e.message ?: "Bad request").closing()
        } catch (e: Exception) {
            text(Response.Status.INTERNAL_ERROR, e.message ?: e.javaClass.simpleName).closing()
        }
        if (pin != null) response.addHeader("Set-Cookie", "$COOKIE=$token; Path=/; HttpOnly; SameSite=Strict")
        return response
    }

    /** Null if [pin] is right. The PIN is short, so wrong guesses lock everyone out for a while. */
    @Synchronized
    private fun checkPin(pin: String): Pair<Response.Status, PinError>? {
        if (System.currentTimeMillis() < lockedUntil) {
            return Response.Status.TOO_MANY_REQUESTS to PinError.LockedOut
        }
        if (!tokenMatches(pin.filter(Char::isDigit))) {
            if (++failures >= MAX_FAILURES) {
                failures = 0
                lockedUntil = System.currentTimeMillis() + LOCKOUT_MS
            }
            return Response.Status.UNAUTHORIZED to PinError.Wrong
        }
        failures = 0
        return null
    }

    private enum class PinError(private val en: String, private val ar: String) {
        Wrong("Wrong PIN. Check the car screen.", "الرمز السري غير صحيح. تحقق من شاشة السيارة."),
        LockedOut("Too many wrong PINs. Wait a moment and try again.", "محاولات خاطئة كثيرة. انتظر قليلًا ثم حاول مجددًا.");

        fun message(arabic: Boolean) = if (arabic) ar else en
    }

    /** The device's first preferred language, from the browser's Accept-Language header. */
    private fun prefersArabic(session: IHTTPSession) =
        session.headers["accept-language"].orEmpty().trimStart().lowercase().startsWith("ar")

    private fun font(name: String): Response {
        val bytes = name.takeIf { FONT_NAME.matches(it) }?.let { asset("fonts/$it") }
            ?: return text(Response.Status.NOT_FOUND, "Not found")
        val mime = if (name.endsWith(".woff2")) "font/woff2" else "font/ttf"
        return newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong())
            .apply { addHeader("Cache-Control", "public, max-age=31536000, immutable") }
    }

    private fun pinPage(status: Response.Status, error: String?, arabic: Boolean): Response {
        val html = PIN_PAGE
            .replace("{{lang}}", if (arabic) "ar" else "en")
            .replace("{{dir}}", if (arabic) "rtl" else "ltr")
            .replace("{{prompt}}", if (arabic) "أدخل الرمز السري الظاهر على شاشة السيارة" else "Enter the PIN shown on the car screen")
            .replace("{{connect}}", if (arabic) "اتصال" else "Connect")
            .replace("{{error}}", error?.let { "<p class=\"err\">$it</p>" } ?: "")
        return newFixedLengthResponse(status, "text/html; charset=utf-8", html)
            .apply { addHeader("Cache-Control", "no-store") }
    }

    private fun page(): Response {
        val html = pageHtml()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html.inputStream(), html.size.toLong())
            .apply { addHeader("Cache-Control", "no-store") }
    }

    private fun list(session: IHTTPSession): Response {
        val dir = resolve(param(session, "path") ?: home)
        if (!dir.isDirectory) throw BadRequest("Not a folder")
        val entries = JSONArray()
        for (f in dir.listFiles().orEmpty().filterNot { it.name.endsWith(PART) }.sortedWith(FILE_ORDER)) {
            entries.put(
                JSONObject()
                    .put("name", f.name)
                    .put("dir", f.isDirectory)
                    .put("size", if (f.isFile) f.length() else 0)
                    .put("modified", f.lastModified())
            )
        }
        val json = JSONObject()
            .put("path", relative(dir))
            .put("home", home)
            .put("free", root.usableSpace)
            .put("entries", entries)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun upload(session: IHTTPSession): Response {
        val dir = resolve(param(session, "path").orEmpty())
        val name = safeName(param(session, "name"))
        val length = session.headers["content-length"]?.toLongOrNull() ?: throw BadRequest("Missing Content-Length")
        if (!dir.isDirectory) throw BadRequest("Not a folder")

        val target = uniqueFile(dir, name)
        val part = File(dir, target.name + PART)
        val id = nextId.incrementAndGet()
        listener.onUploadStarted(id, target.name, length)
        var ok = false
        try {
            part.outputStream().buffered(BUFFER).use { out ->
                copy(session.inputStream, out, length) { listener.onUploadProgress(id, it) }
            }
            if (!part.renameTo(target)) throw IOException("Could not save ${target.name}")
            ok = true
        } finally {
            if (!ok) part.delete()
            listener.onUploadFinished(id, target.takeIf { ok })
            listener.onFilesChanged()
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().put("name", target.name).toString())
    }

    // ---------------------------------------------------------------- resumable uploads
    // The device picks a stable id per file. Bytes go to a hidden ".<id>.part" in the target folder;
    // after a dropped connection the device asks how much arrived and sends the rest.

    private fun uploadStatus(session: IHTTPSession): Response {
        val part = partFile(session)
        return json(JSONObject().put("received", if (part.isFile) part.length() else 0L))
    }

    private fun resumableUpload(session: IHTTPSession): Response {
        val part = partFile(session)
        val dir = part.parentFile!!
        val name = safeName(param(session, "name"))
        val uploadId = param(session, "id")!!
        val offset = param(session, "offset")?.toLongOrNull() ?: throw BadRequest("Missing offset")
        val total = param(session, "total")?.toLongOrNull() ?: throw BadRequest("Missing total")
        val length = session.headers["content-length"]?.toLongOrNull() ?: throw BadRequest("Missing Content-Length")
        if (offset < 0 || offset + length > total) throw BadRequest("Bad range")

        // An old connection for this id may still be draining; the device retries shortly.
        if (!activeUploads.add(uploadId)) return conflict(part)
        try {
            val received = if (part.isFile) part.length() else 0L
            if (offset != received) return conflict(part)

            val id = transferIds.getOrPut(uploadId) {
                nextId.incrementAndGet().also { listener.onUploadStarted(it, name, total) }
            }
            listener.onUploadProgress(id, offset)
            try {
                FileOutputStream(part, true).buffered(BUFFER).use { out ->
                    copy(session.inputStream, out, length) { listener.onUploadProgress(id, offset + it) }
                }
            } catch (e: IOException) {
                listener.onUploadPaused(id)
                throw e
            }
            if (part.length() < total) {
                return json(JSONObject().put("received", part.length()))
            }

            transferIds.remove(uploadId)
            val target = uniqueFile(dir, name)
            val ok = part.renameTo(target)
            listener.onUploadFinished(id, target.takeIf { ok })
            listener.onFilesChanged()
            if (!ok) throw IOException("Could not save ${target.name}")
            return json(JSONObject().put("name", target.name))
        } finally {
            activeUploads.remove(uploadId)
        }
    }

    private fun partFile(session: IHTTPSession): File {
        val dir = resolve(param(session, "path").orEmpty())
        if (!dir.isDirectory) throw BadRequest("Not a folder")
        val id = param(session, "id")?.takeIf { UPLOAD_ID.matches(it) } ?: throw BadRequest("Invalid upload id")
        return File(dir, ".$id$PART")
    }

    private fun conflict(part: File) = newFixedLengthResponse(
        Response.Status.CONFLICT, "application/json",
        JSONObject().put("received", if (part.isFile) part.length() else 0L).toString(),
    ).closing()

    private fun json(body: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json", body.toString())

    /** Copies exactly [length] bytes, reporting the running count about every [REPORT_EVERY] bytes. */
    private fun copy(input: InputStream, out: OutputStream, length: Long, progress: (Long) -> Unit) {
        val buf = ByteArray(BUFFER)
        var done = 0L
        var sinceReport = 0L
        while (done < length) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), length - done).toInt())
            if (n < 0) throw IOException("Upload interrupted")
            out.write(buf, 0, n)
            done += n
            sinceReport += n
            if (sinceReport >= REPORT_EVERY) {
                progress(done)
                sinceReport = 0
            }
        }
    }

    private fun download(session: IHTTPSession): Response {
        val file = resolve(param(session, "path").orEmpty())
        if (!file.isFile) throw BadRequest("Not a file")
        val encoded = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        return newFixedLengthResponse(Response.Status.OK, mime, file.inputStream(), file.length()).apply {
            addHeader("Content-Disposition", "attachment; filename*=UTF-8''$encoded")
        }
    }

    private fun receiveText(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: throw BadRequest("Missing length")
        if (length > MAX_TEXT) throw BadRequest("Text is too long")
        val bytes = ByteArray(length.toInt())
        var read = 0
        while (read < bytes.size) {
            val n = session.inputStream.read(bytes, read, bytes.size - read)
            if (n < 0) throw IOException("Connection closed")
            read += n
        }
        val text = String(bytes, Charsets.UTF_8).trim()
        if (text.isEmpty()) throw BadRequest("Nothing to send")
        listener.onTextReceived(text)
        return text(Response.Status.OK, "ok")
    }

    private fun mkdir(session: IHTTPSession): Response {
        val dir = File(resolve(param(session, "path").orEmpty()), safeName(param(session, "name")))
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Could not create folder")
        listener.onFilesChanged()
        return text(Response.Status.OK, "ok")
    }

    private fun delete(session: IHTTPSession): Response {
        val file = resolve(param(session, "path").orEmpty())
        if (file == root.canonicalFile || file.parentFile == root.canonicalFile && file.name == "Android") {
            throw BadRequest("Can't delete ${file.name.ifEmpty { "the root folder" }}")
        }
        if (!file.deleteRecursively()) throw IOException("Could not delete ${file.name}")
        listener.onFilesChanged()
        return text(Response.Status.OK, "ok")
    }

    // ---------------------------------------------------------------- helpers

    private fun resolve(path: String): File {
        val file = File(root, path).canonicalFile
        val rootPath = root.canonicalFile
        if (file != rootPath && !file.path.startsWith(rootPath.path + File.separator)) throw BadRequest("Invalid path")
        return file
    }

    private fun relative(file: File) = file.canonicalFile.toRelativeString(root.canonicalFile)

    private fun safeName(name: String?): String {
        val clean = name?.trim()?.replace(Regex("""[/\\\u0000]"""), "_")
        if (clean.isNullOrEmpty() || clean == "." || clean == "..") throw BadRequest("Invalid name")
        return clean
    }

    private fun tokenMatches(candidate: String) =
        MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())

    private fun param(session: IHTTPSession, name: String) = session.parameters[name]?.firstOrNull()

    private fun text(status: Response.Status, body: String) =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", body)

    /** For error paths: the request body may be unread, so don't reuse the connection. */
    private fun Response.closing() = apply { addHeader("Connection", "close") }

    private class BadRequest(message: String) : Exception(message)

    companion object {
        private const val COOKIE = "motordash"
        private const val MAX_FAILURES = 5
        private const val LOCKOUT_MS = 30_000L
        private const val MAX_TEXT = 16 * 1024
        const val PART = ".part"
        private val UPLOAD_ID = Regex("[A-Za-z0-9_-]{8,64}")
        private const val BUFFER = 256 * 1024
        private const val REPORT_EVERY = 1L shl 20

        // Shown when the device opens the address without the QR code's PIN.
        private val FONT_NAME = Regex("Dubai-(Light|Regular|Medium|Bold)\\.(woff2|ttf)")

        private val PIN_PAGE = """<!doctype html><html lang="{{lang}}" dir="{{dir}}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>MotorDash</title>
<style>
  @font-face { font-family: Dubai; src: url(/fonts/Dubai-Regular.woff2) format("woff2"); font-weight: 400; font-display: swap; }
  @font-face { font-family: Dubai; src: url(/fonts/Dubai-Bold.woff2) format("woff2"); font-weight: 600 700; font-display: swap; }
  body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
    background: #000; color: #f2f2f2; font-family: Dubai, system-ui, sans-serif; }
  form { width: min(360px, 90vw); text-align: center; }
  h1 { font-size: 26px; font-weight: 600; margin: 0 0 8px; }
  p { color: #9a9a9a; margin: 0 0 24px; }
  input { box-sizing: border-box; width: 100%; padding: 16px; font-size: 32px; letter-spacing: 12px; text-align: center; direction: ltr;
    background: #121212; color: #fff; border: 1px solid #242424; border-radius: 16px; outline: none; }
  input:focus { border-color: #2f7bff; }
  button { width: 100%; margin-top: 16px; padding: 16px; font-size: 18px; font-weight: 600; color: #fff;
    background: #2f7bff; border: 0; border-radius: 999px; }
  .err { color: #ff5a5a; margin: 16px 0 0; }
</style></head><body>
<form method="get" action="/">
  <h1>MotorDash</h1>
  <p>{{prompt}}</p>
  <input name="k" inputmode="numeric" pattern="[0-9 ]*" maxlength="7" autocomplete="off" autofocus required>
  <button>{{connect}}</button>
  {{error}}
</form></body></html>"""

        val FILE_ORDER = compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })

        fun uniqueFile(dir: File, name: String): File {
            var file = File(dir, name)
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            var n = 1
            while (file.exists() || File(dir, file.name + PART).exists()) {
                file = File(dir, "$base (${n++})$ext")
            }
            return file
        }
    }
}
