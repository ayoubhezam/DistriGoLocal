package com.distrigo.app.data.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Seals the anchor state so that an edit to its file is seen. On the phone: an HMAC key in the Keystore ([KeystoreSeal]). */
fun interface StateSeal {
    fun tag(data: ByteArray): ByteArray
}

/**
 * The license and what the phone knows since its last check-in, in `no_backup/license/`.
 *
 *  - `license.jws`: the token as the server sent it. Its signature is all the protection it needs.
 *  - `anchor.state`: the [AnchorState] as JSON on the first line, its seal (base64url) on the second. An
 *    edited or missing state reads as none, and only a check-in makes a new one.
 *  - `revoked`: present once the server has said this phone no longer holds the subscription.
 *
 * Why `no_backup`, like DeviceIdentity: Auto Backup and a device-to-device transfer copy the database and
 * SharedPreferences, and must not carry a license or a clock onto another phone. Nothing here travels in a
 * `.distrigo` backup either. App updates keep these files, so an update never signs anyone out.
 */
class LicenseStore(private val dir: File, private val seal: StateSeal) : AnchorStore {

    private val tokenFile get() = File(dir, "license.jws")
    private val anchorFile get() = File(dir, "anchor.state")
    private val revokedFile get() = File(dir, "revoked")

    fun readToken(): String? = synchronized(LOCK) {
        tokenFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun writeToken(token: String) = synchronized(LOCK) { write(tokenFile, token) }

    fun isRevoked(): Boolean = synchronized(LOCK) { revokedFile.isFile }

    fun setRevoked(revoked: Boolean) {
        synchronized(LOCK) {
            if (revoked) write(revokedFile, "") else revokedFile.delete()
        }
    }

    /** Forgets everything: a sign-out. Never called by an update. */
    fun clear() {
        synchronized(LOCK) {
            tokenFile.delete()
            anchorFile.delete()
            revokedFile.delete()
        }
    }

    override fun load(): AnchorState? = synchronized(LOCK) {
        val lines = anchorFile.takeIf { it.isFile }?.readLines() ?: return null
        val body = lines.getOrNull(0) ?: return null
        val tag = lines.getOrNull(1)?.trim()?.let(LicenseCrypto::fromBase64Url) ?: return null
        if (!MessageDigest.isEqual(seal.tag(body.toByteArray(Charsets.UTF_8)), tag)) return null
        parse(body)
    }

    override fun save(state: AnchorState) = synchronized(LOCK) {
        val body = JsonObject().apply {
            addProperty("v", FORMAT)
            addProperty("floor", state.floor)
            addProperty("elapsed", state.elapsed)
            addProperty("boot", state.boot)
            addProperty("reboots", state.reboots)
            addProperty("anchored_at", state.anchoredAt)
            addProperty("max_seq", state.maxSeq)
        }.toString()
        val tag = LicenseCrypto.base64Url(seal.tag(body.toByteArray(Charsets.UTF_8)))
        write(anchorFile, "$body\n$tag\n")
    }

    private fun parse(body: String): AnchorState? =
        try {
            val json = JsonParser().parse(body).asJsonObject
            // A later format is not guessed at: it reads as no state, which a check-in replaces.
            if (json.get("v")?.asInt != FORMAT) {
                null
            } else {
                AnchorState(
                    floor = json.get("floor").asLong,
                    elapsed = json.get("elapsed").asLong,
                    boot = json.get("boot")?.takeIf { it.isJsonPrimitive }?.asInt,
                    reboots = json.get("reboots").asInt,
                    anchoredAt = json.get("anchored_at").asLong,
                    maxSeq = json.get("max_seq").asLong,
                )
            }
        } catch (e: RuntimeException) {
            null
        }

    /** Written whole and moved into place, so a kill mid-write keeps the previous file. */
    private fun write(file: File, text: String) {
        dir.mkdirs()
        val temp = File(dir, "${file.name}.tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private val LOCK = Any()
        private const val FORMAT = 1

        fun forApp(context: android.content.Context) =
            LicenseStore(File(context.applicationContext.noBackupFilesDir, "license"), KeystoreSeal())
    }
}
