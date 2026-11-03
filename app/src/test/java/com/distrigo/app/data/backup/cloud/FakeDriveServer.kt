package com.distrigo.app.data.backup.cloud

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Enough of Drive v3 for the backup, on MockWebServer: `appDataFolder` files with `appProperties`, list queries with
 * paging, multipart and resumable uploads (308 + Range), downloads with Range, delete, `about`.
 * Faults are queued by the test: an error answer, a cut connection, a short acknowledgement, a corrupted store.
 */
internal class FakeDriveServer : Dispatcher() {
    class StoredFile(val id: String, val name: String, val properties: Map<String, String>, var bytes: ByteArray)
    private class Session(val name: String, val properties: Map<String, String>, val total: Long) {
        val received = ByteArrayOutputStream()
        var done: JsonObject? = null
    }

    val server = MockWebServer().also { it.dispatcher = this }
    val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    val files = LinkedHashMap<String, StoredFile>()
    private val sessions = HashMap<String, Session>()
    private var nextId = 1

    var email = "owner@gmail.com"
    var acceptedToken = "token-1"
    var pageSize = 2

    /** Answers to give, in order, before serving normally. A null entry cuts the connection. */
    val faults = ArrayDeque<MockResponse?>()

    /** Only this many bytes of the next chunk are kept (Drive may acknowledge less than sent). */
    var acknowledgeOnlyNext: Int? = null

    /** The next completed upload is stored with one byte changed. */
    var corruptNextUpload = false

    /** Downloads answer 200 with the whole file whatever the Range. */
    var ignoreRange = false

    /** Forget every upload session (Drive expires them). */
    fun expireSessions() = synchronized(this) { sessions.clear() }

    var chunkBytesReceived = 0L
        private set

    fun error(code: Int, reason: String? = null): MockResponse = MockResponse().setResponseCode(code).setBody(
        if (reason == null) "{}" else """{"error":{"code":$code,"errors":[{"reason":"$reason"}]}}""",
    )

    override fun dispatch(request: RecordedRequest): MockResponse = synchronized(this) {
        if (request.getHeader("Authorization") != "Bearer $acceptedToken") return error(401, "authError")
        if (faults.isNotEmpty()) {
            return faults.removeFirst() ?: MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val url = request.requestUrl!!
        val path = url.encodedPath
        val method = request.method
        return when {
            method == "GET" && path == "/drive/v3/about" ->
                json(JsonObject().apply { add("user", JsonObject().apply { addProperty("emailAddress", email) }) })
            method == "GET" && path == "/drive/v3/files" -> list(url.queryParameter("q")!!, url.queryParameter("pageToken"))
            method == "GET" && path.startsWith("/drive/v3/files/") -> download(path.substringAfterLast('/'), request)
            method == "DELETE" && path.startsWith("/drive/v3/files/") ->
                if (files.remove(path.substringAfterLast('/')) != null) MockResponse().setResponseCode(204) else error(404, "notFound")
            method == "POST" && path == "/upload/drive/v3/files" && url.queryParameter("uploadType") == "multipart" ->
                multipartCreate(request)
            method == "PATCH" && path.startsWith("/upload/drive/v3/files/") -> multipartUpdate(path.substringAfterLast('/'), request)
            method == "POST" && path == "/upload/drive/v3/files" && url.queryParameter("uploadType") == "resumable" ->
                startSession(request)
            method == "PUT" && path.startsWith("/upload/session/") -> chunk(path.substringAfterLast('/'), request)
            else -> error(400, "badRequest")
        }
    }

    private fun list(query: String, pageToken: String?): MockResponse {
        require(query.contains("'appDataFolder' in parents") && query.contains("trashed = false")) { query }
        val wanted = Regex("""key='([^']*)' and value='((?:\\.|[^'\\])*)'""").findAll(query)
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("\\\\", "\\") }
        val matching = files.values.filter { file -> wanted.all { (k, v) -> file.properties[k] == v } }
        val start = pageToken?.toInt() ?: 0
        val page = matching.drop(start).take(pageSize)
        return json(JsonObject().apply {
            add("files", JsonArray().apply { page.forEach { add(fileJson(it)) } })
            if (start + pageSize < matching.size) addProperty("nextPageToken", (start + pageSize).toString())
        })
    }

    private fun download(id: String, request: RecordedRequest): MockResponse {
        val file = files[id] ?: return error(404, "notFound")
        val range = request.getHeader("Range")
        if (range == null || ignoreRange) return MockResponse().setBody(okio.Buffer().write(file.bytes))
        val from = range.removePrefix("bytes=").substringBefore('-').toInt()
        return MockResponse().setResponseCode(206).setBody(okio.Buffer().write(file.bytes.copyOfRange(from, file.bytes.size)))
    }

    private fun multipartCreate(request: RecordedRequest): MockResponse {
        val (metadata, bytes) = parts(request)
        val parents = metadata.getAsJsonArray("parents")
        require(parents != null && parents.single().asString == "appDataFolder")
        return json(fileJson(store(metadata.get("name").asString, properties(metadata), bytes)))
    }

    private fun multipartUpdate(id: String, request: RecordedRequest): MockResponse {
        val file = files[id] ?: return error(404, "notFound")
        file.bytes = parts(request).second
        return json(fileJson(file))
    }

    private fun startSession(request: RecordedRequest): MockResponse {
        val metadata = JsonParser().parse(request.body.readUtf8()).asJsonObject
        require(metadata.getAsJsonArray("parents").single().asString == "appDataFolder")
        val total = request.getHeader("X-Upload-Content-Length")!!.toLong()
        val id = "s${nextId++}"
        sessions[id] = Session(metadata.get("name").asString, properties(metadata), total)
        return MockResponse().setHeader("Location", server.url("/upload/session/$id").toString())
    }

    private fun chunk(id: String, request: RecordedRequest): MockResponse {
        val session = sessions[id] ?: return error(404, "notFound")
        // Drive keeps a finished session: asking about it again returns the file.
        session.done?.let { return json(it) }
        val range = request.getHeader("Content-Range")!!.removePrefix("bytes ")
        val body = request.body.readByteArray()
        if (!range.startsWith("*")) {
            val start = range.substringBefore('-').toLong()
            if (start != session.received.size().toLong()) return error(400, "badContentRange")
            val keep = acknowledgeOnlyNext?.coerceAtMost(body.size) ?: body.size
            acknowledgeOnlyNext = null
            session.received.write(body, 0, keep)
            chunkBytesReceived += keep
        }
        val received = session.received.size().toLong()
        if (received == session.total) {
            var bytes = session.received.toByteArray()
            if (corruptNextUpload && bytes.isNotEmpty()) {
                corruptNextUpload = false
                bytes = bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
            }
            val file = fileJson(store(session.name, session.properties, bytes))
            session.done = file
            return json(file)
        }
        return MockResponse().setResponseCode(308).apply { if (received > 0) setHeader("Range", "bytes=0-${received - 1}") }
    }

    private fun store(name: String, properties: Map<String, String>, bytes: ByteArray): StoredFile {
        val file = StoredFile("f${nextId++}", name, properties, bytes)
        files[file.id] = file
        return file
    }

    private fun properties(metadata: JsonObject): Map<String, String> =
        metadata.getAsJsonObject("appProperties")?.entrySet()?.associate { (k, v) -> k to v.asString }.orEmpty()

    /** Splits a multipart/related body (metadata JSON, then the media) without decoding the media as text. */
    private fun parts(request: RecordedRequest): Pair<JsonObject, ByteArray> {
        val boundary = request.getHeader("Content-Type")!!.substringAfter("boundary=").trim('"')
        val body = String(request.body.readByteArray(), Charsets.ISO_8859_1)
        val sections = body.split("--$boundary").drop(1).dropLast(1).map { part ->
            part.substringAfter("\r\n\r\n").removeSuffix("\r\n")
        }
        return JsonParser().parse(sections[0]).asJsonObject to sections[1].toByteArray(Charsets.ISO_8859_1)
    }

    private fun fileJson(file: StoredFile) = JsonObject().apply {
        addProperty("id", file.id)
        addProperty("name", file.name)
        addProperty("size", file.bytes.size.toString())
        addProperty("md5Checksum", md5(file.bytes))
        add("appProperties", JsonObject().apply { file.properties.forEach { (k, v) -> addProperty(k, v) } })
    }

    private fun json(body: JsonObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())

    companion object {
        fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** Tokens that count refreshes; [next] is what a refresh hands out. */
internal class CountingTokens(var current: String = "token-1", var next: String = "token-1") : DriveTokens {
    val refreshed = mutableListOf<String>()
    override suspend fun accessToken() = current
    override suspend fun refresh(rejected: String): String {
        refreshed += rejected
        current = next
        return current
    }
}
