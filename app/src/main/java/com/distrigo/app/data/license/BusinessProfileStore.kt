package com.distrigo.app.data.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The last [BusinessProfile] the server gave, in `no_backup/auth/business_profile.json`, so Compte shows the
 * business without Internet. Kept with the account it was read for: another account signed in here never sees
 * it. Plain JSON: a name and a phone the receipts print anyway, nothing that opens anything.
 */
class BusinessProfileStore(private val dir: File) {

    data class Saved(val userId: String, val profile: BusinessProfile)

    private val file get() = File(dir, "business_profile.json")

    private val _saved = MutableStateFlow<Saved?>(null)
    /** What [load] read or [save] wrote; null until then, or when there is none. */
    val saved: StateFlow<Saved?> = _saved.asStateFlow()

    /** Reads the stored profile. File I/O: off the main thread. */
    fun load(): Saved? = synchronized(LOCK) {
        val read = try {
            if (!file.isFile) null else fromJson(JsonParser().parse(file.readText(Charsets.UTF_8)).asJsonObject)
        } catch (e: Exception) {
            null
        }
        _saved.value = read
        read
    }

    /** Written whole and moved into place: a kill mid-write keeps the previous one. */
    fun save(userId: String, profile: BusinessProfile) {
        synchronized(LOCK) {
            dir.mkdirs()
            val json = JsonObject().apply {
                addProperty("v", 1)
                addProperty("user_id", userId)
                addProperty("business_id", profile.businessId)
                addProperty("name", profile.name)
                addProperty("contact_phone", profile.contactPhone)
                addProperty("activity", profile.activity)
                addProperty("sales_reps", profile.salesReps)
                addProperty("can_edit", profile.canEdit)
            }
            val temp = File(dir, "business_profile.json.tmp")
            temp.writeText(json.toString(), Charsets.UTF_8)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            _saved.value = Saved(userId, profile)
        }
    }

    /** The account and its business were deleted, or the account has none. */
    fun clear() {
        synchronized(LOCK) {
            file.delete()
            _saved.value = null
        }
    }

    private fun fromJson(json: JsonObject): Saved? {
        if (json.get("v")?.asInt != 1) return null
        fun text(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        return Saved(
            userId = text("user_id") ?: return null,
            profile = BusinessProfile(
                businessId = text("business_id") ?: return null,
                name = text("name") ?: return null,
                contactPhone = text("contact_phone"),
                activity = text("activity"),
                salesReps = text("sales_reps"),
                canEdit = json.get("can_edit")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
            ),
        )
    }

    companion object {
        private val LOCK = Any()

        fun forApp(context: android.content.Context) =
            BusinessProfileStore(File(context.applicationContext.noBackupFilesDir, "auth"))
    }
}
