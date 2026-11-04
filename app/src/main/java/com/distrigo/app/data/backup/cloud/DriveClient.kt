package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.backup.cloud.CloudBackupException.Problem
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/** Access tokens for Drive. On the phone: [DriveAuthorizer], silently; in tests: a fixed text. */
interface DriveTokens {
    /** A token for the `drive.appdata` scope; throws [CloudBackupException] (e.g. [Problem.NEEDS_CONSENT]). */
    suspend fun accessToken(): String

    /** Drive refused [rejected] (401): forget it and return a fresh one. */
    suspend fun refresh(rejected: String): String
}

/** A file in the app's hidden Drive folder, with the fields this app reads. */
data class DriveFile(
    val id: String,
    val name: String,
    val size: Long,
    val md5: String?,
    val appProperties: Map<String, String>,
)

/**
 * The few Drive v3 calls the backup needs, on OkHttp rather than Google's Java client (plan §3.1). Everything lives
 * in `appDataFolder`, the app's own hidden folder: the `drive.appdata` scope sees nothing else of the user's Drive.
 *
 * A 401 gets one fresh token and one retry; every other failure becomes a [CloudBackupException].
 */
class DriveClient(
    private val http: OkHttpClient,
    private val tokens: DriveTokens,
    baseUrl: String = "https://www.googleapis.com",
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    init {
        require(chunkSize > 0 && chunkSize % CHUNK_UNIT == 0) { "Chunks are whole multiples of 256 KiB" }
    }

    /** The account's email, from `about` (allowed by `drive.appdata`, no extra scope). */
    suspend fun accountEmail(): String? {
        val url = base.newBuilder().addPathSegments("drive/v3/about").addQueryParameter("fields", "user(emailAddress)").build()
        val json = callJson { Request.Builder().url(url).get() }
        return json.getAsJsonObject("user")?.get("emailAddress")?.asString
    }

    /** Every file of `appDataFolder` matching [query] (Drive's query language), all pages read. */
    suspend fun list(query: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = base.newBuilder().addPathSegments("drive/v3/files")
                .addQueryParameter("spaces", APP_FOLDER)
                .addQueryParameter("q", "'$APP_FOLDER' in parents and trashed = false and ($query)")
                .addQueryParameter("fields", "nextPageToken,files($FILE_FIELDS)")
                .addQueryParameter("pageSize", "1000")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val json = callJson { Request.Builder().url(url).get() }
            json.getAsJsonArray("files")?.forEach { files += fileFrom(it.asJsonObject) }
            pageToken = json.get("nextPageToken")?.asString
        } while (pageToken != null)
        return files
    }

    /** A new small file (≤ 5 MB), metadata and bytes in one request. */
    suspend fun createSmall(name: String, appProperties: Map<String, String>, bytes: ByteArray): DriveFile {
        val metadata = metadata(name, appProperties, inAppFolder = true)
        return multipart(metadata, bytes) { url, body -> Request.Builder().url(url).post(body) }
    }

    /** Replaces a small file's bytes; its name and properties stay. */
    suspend fun updateSmall(id: String, bytes: ByteArray): DriveFile =
        multipart(JsonObject(), bytes, fileId = id) { url, body -> Request.Builder().url(url).patch(body) }

    suspend fun download(id: String): ByteArray = open(id, 0).use { it.readBytes() }

    /** The file's bytes from [fromByte]. The caller closes the stream. */
    suspend fun open(id: String, fromByte: Long): InputStream {
        val url = fileUrl(id).newBuilder().addQueryParameter("alt", "media").build()
        val response = call {
            Request.Builder().url(url).get().apply { if (fromByte > 0) header("Range", "bytes=$fromByte-") }
        }
        val body = response.body ?: run {
            response.close()
            throw CloudBackupException(Problem.REJECTED, "Empty download")
        }
        val stream = object : FilterInputStream(body.byteStream()) {
            override fun close() = response.close()
        }
        // A server that ignores Range answers 200 with the whole file: skip to the requested byte.
        if (fromByte > 0 && response.code == 200) {
            withContext(Dispatchers.IO) {
                var left = fromByte
                while (left > 0) {
                    val skipped = stream.skip(left)
                    if (skipped <= 0) {
                        if (stream.read() < 0) throw CloudBackupException(Problem.NETWORK, "File shorter than $fromByte")
                        left--
                    } else {
                        left -= skipped
                    }
                }
            }
        }
        return stream
    }

    /** Deleted for good (not to the trash: `appDataFolder` has none). Already gone counts as deleted. */
    suspend fun delete(id: String) {
        try {
            call { Request.Builder().url(fileUrl(id)).delete() }.close()
        } catch (e: CloudBackupException) {
            if (e.problem != Problem.NOT_FOUND) throw e
        }
    }

    /**
     * Sends [file] with Drive's resumable upload, [chunkSize] bytes per request. [resume] is a session URI given
     * earlier to [onSession]; if Drive no longer knows it, a new session starts.
     */
    suspend fun uploadResumable(
        file: File,
        name: String,
        appProperties: Map<String, String>,
        resume: String?,
        onSession: (String) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): DriveFile {
        val total = file.length()
        var session = resume
        var offset = 0L
        if (session != null) {
            when (val status = uploadStatus(session, total)) {
                is UploadStatus.Done -> return status.file
                is UploadStatus.Received -> offset = status.next
                UploadStatus.Gone -> session = null
            }
        }
        var restarted = false
        while (true) {
            if (session == null) {
                session = startSession(name, appProperties, total)
                onSession(session)
                offset = 0
            }
            onProgress(offset, total)
            val length = minOf(chunkSize.toLong(), total - offset)
            when (val status = sendChunk(session, file, offset, length, total)) {
                is UploadStatus.Done -> {
                    onProgress(total, total)
                    return status.file
                }
                is UploadStatus.Received -> offset = status.next
                UploadStatus.Gone -> {
                    // Expired mid-upload: one fresh session from the start, then give up until the next run.
                    if (restarted) throw CloudBackupException(Problem.NETWORK, "Upload session lost twice")
                    restarted = true
                    session = null
                }
            }
        }
    }

    /** Revokes the app's access to the account (« Délier »). Failure is ignored: the link is forgotten anyway. */
    suspend fun revoke(token: String, revokeUrl: String = REVOKE_URL) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(revokeUrl)
                    .post(FormBody.Builder().add("token", token).build())
                    .build()
                http.newCall(request).execute().close()
            } catch (e: IOException) {
                // Offline: the grant stays until the user removes it in their Google account.
            }
        }
    }

    // ---- resumable upload steps ----

    private sealed class UploadStatus {
        class Done(val file: DriveFile) : UploadStatus()
        class Received(val next: Long) : UploadStatus()
        object Gone : UploadStatus()
    }

    private suspend fun startSession(name: String, appProperties: Map<String, String>, total: Long): String {
        val url = base.newBuilder().addPathSegments("upload/drive/v3/files")
            .addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", FILE_FIELDS)
            .build()
        val metadata = metadata(name, appProperties, inAppFolder = true).toString().toRequestBody(JSON)
        val response = call {
            Request.Builder().url(url).post(metadata)
                .header("X-Upload-Content-Type", OCTET_STREAM.toString())
                .header("X-Upload-Content-Length", total.toString())
        }
        response.use {
            return it.header("Location") ?: throw CloudBackupException(Problem.REJECTED, "No upload session")
        }
    }

    private suspend fun uploadStatus(session: String, total: Long): UploadStatus =
        put(session, ByteArray(0).toRequestBody(null), "bytes */$total")

    private suspend fun sendChunk(session: String, file: File, offset: Long, length: Long, total: Long): UploadStatus {
        val range = if (length == 0L) "bytes */$total" else "bytes $offset-${offset + length - 1}/$total"
        return put(session, FileSliceBody(file, offset, length), range)
    }

    private suspend fun put(session: String, body: RequestBody, contentRange: String): UploadStatus {
        val response = try {
            call(allowed = setOf(RESUME_INCOMPLETE)) {
                Request.Builder().url(session).put(body).header("Content-Range", contentRange)
            }
        } catch (e: CloudBackupException) {
            // 404 or 410: the session expired (Drive keeps them about a week) or was never Drive's.
            if (e.problem == Problem.NOT_FOUND || e.message == GONE_MESSAGE) return UploadStatus.Gone
            throw e
        }
        response.use {
            if (it.code == RESUME_INCOMPLETE) {
                // « Range: bytes=0-N »: Drive holds bytes up to N. No header: it holds none yet.
                val end = it.header("Range")?.substringAfterLast('-')?.toLongOrNull()
                return UploadStatus.Received(if (end == null) 0 else end + 1)
            }
            return UploadStatus.Done(fileFrom(parse(it)))
        }
    }

    /** A slice of a file as a request body, read again on each attempt (OkHttp may retry). */
    private class FileSliceBody(private val file: File, private val offset: Long, private val length: Long) : RequestBody() {
        override fun contentType(): MediaType = OCTET_STREAM
        override fun contentLength() = length
        override fun writeTo(sink: BufferedSink) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val buffer = ByteArray(64 * 1024)
                var left = length
                while (left > 0) {
                    val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) throw EOFException("File shrank during upload")
                    sink.write(buffer, 0, read)
                    left -= read
                }
            }
        }
    }

    // ---- requests ----

    private suspend fun multipart(
        metadata: JsonObject,
        bytes: ByteArray,
        fileId: String? = null,
        method: (HttpUrl, RequestBody) -> Request.Builder,
    ): DriveFile {
        val url = base.newBuilder().addPathSegments("upload/drive/v3/files")
            .apply { fileId?.let { addPathSegment(it) } }
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", FILE_FIELDS)
            .build()
        val body = MultipartBody.Builder().setType(RELATED)
            .addPart(metadata.toString().toRequestBody(JSON))
            .addPart(bytes.toRequestBody(OCTET_STREAM))
            .build()
        return fileFrom(callJson { method(url, body) })
    }

    private fun metadata(name: String, appProperties: Map<String, String>, inAppFolder: Boolean) = JsonObject().apply {
        addProperty("name", name)
        if (inAppFolder) add("parents", com.google.gson.JsonArray().apply { add(APP_FOLDER) })
        add("appProperties", JsonObject().apply { appProperties.forEach { (k, v) -> addProperty(k, v) } })
    }

    private fun fileUrl(id: String) = base.newBuilder().addPathSegments("drive/v3/files").addPathSegment(id).build()

    private suspend fun callJson(build: () -> Request.Builder): JsonObject = call(build = build).use { parse(it) }

    /**
     * Runs a request with a token; a 401 gets a fresh token and one retry. Returns the open response when its code is
     * 2xx or in [allowed]; anything else is mapped and thrown.
     */
    private suspend fun call(allowed: Set<Int> = emptySet(), build: () -> Request.Builder): Response {
        var token = tokens.accessToken()
        var response = execute(build, token)
        if (response.code == 401) {
            response.close()
            token = tokens.refresh(token)
            response = execute(build, token)
            if (response.code == 401) {
                response.close()
                throw CloudBackupException(Problem.NEEDS_CONSENT, "Drive refused a fresh token")
            }
        }
        if (response.isSuccessful || response.code in allowed) return response
        throw response.use { failure(it) }
    }

    private suspend fun execute(build: () -> Request.Builder, token: String): Response = withContext(Dispatchers.IO) {
        try {
            http.newCall(build().header("Authorization", "Bearer $token").build()).execute()
        } catch (e: IOException) {
            throw CloudBackupException(Problem.NETWORK, e.message ?: "Network", e)
        }
    }

    private fun parse(response: Response): JsonObject = try {
        JsonParser().parse(response.body?.string().orEmpty()).asJsonObject
    } catch (e: Exception) {
        throw CloudBackupException(Problem.REJECTED, "Unreadable answer (${response.code})", e)
    }

    companion object {
        /** 8 MB, a multiple of Drive's 256 KiB unit: few requests, little to resend after a cut. */
        const val DEFAULT_CHUNK_SIZE = 32 * 256 * 1024
        const val CHUNK_UNIT = 256 * 1024

        /** Drive's limit for a one-request upload. */
        const val SMALL_FILE_LIMIT = 5L * 1024 * 1024

        const val APP_FOLDER = "appDataFolder"
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        private const val FILE_FIELDS = "id,name,size,md5Checksum,appProperties"
        private const val RESUME_INCOMPLETE = 308
        private const val GONE_MESSAGE = "Gone"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val RELATED = "multipart/related".toMediaType()

        /** A text value inside a Drive query: `'` and `\` escaped. */
        fun quote(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

        internal fun fileFrom(json: JsonObject) = DriveFile(
            id = json.get("id")?.asString ?: throw CloudBackupException(Problem.REJECTED, "File without id"),
            name = json.get("name")?.asString.orEmpty(),
            size = json.get("size")?.asString?.toLongOrNull() ?: 0,
            md5 = json.get("md5Checksum")?.asString,
            appProperties = json.getAsJsonObject("appProperties")?.entrySet()
                ?.associate { (k, v) -> k to v.asString }
                .orEmpty(),
        )

        /** Drive's error answer (`{"error":{"errors":[{"reason":…}]}}`) as a [Problem]. */
        internal fun failure(response: Response): CloudBackupException {
            val code = response.code
            val reason = try {
                JsonParser().parse(response.body?.string().orEmpty()).asJsonObject
                    .getAsJsonObject("error")?.getAsJsonArray("errors")?.firstOrNull()
                    ?.asJsonObject?.get("reason")?.asString
            } catch (e: Exception) {
                null
            }
            val problem = when {
                code == 403 && reason == "storageQuotaExceeded" -> Problem.QUOTA_EXCEEDED
                code == 403 && reason in setOf("rateLimitExceeded", "userRateLimitExceeded") -> Problem.RATE_LIMITED
                code == 403 && reason in setOf("insufficientPermissions", "appNotAuthorizedToFile", "authError") ->
                    Problem.NEEDS_CONSENT
                code == 404 -> Problem.NOT_FOUND
                code == 410 -> return CloudBackupException(Problem.NOT_FOUND, GONE_MESSAGE)
                code == 429 -> Problem.RATE_LIMITED
                code == 408 || code >= 500 -> Problem.SERVER
                else -> Problem.REJECTED
            }
            val detail = "HTTP $code ${reason.orEmpty()}".trim()
            return CloudBackupException(problem, "Drive $detail", detail = detail)
        }
    }
}
