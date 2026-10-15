package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.Backend
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/** One batch, as codes_by_batch counts its codes. */
data class CodeBatch(
    val name: String,
    val plan: String,
    val days: Int,
    val total: Int,
    val unused: Int,
    /** Codes that opened a business: its key. */
    val keys: Int,
    val renewals: Int,
    val expired: Int,
    val revoked: Int,
    val createdOn: LocalDate?,
)

/** When a batch's unused codes stop working. */
sealed interface CodeExpiry {
    /** In 12 months: the server's default. */
    data object Default : CodeExpiry
    data object Never : CodeExpiry
    data class On(val day: LocalDate) : CodeExpiry
}

/** A batch to make. [businessName]: a company the codes open, so the merchant isn't asked for one. */
data class CodeOrder(
    val count: Int,
    val days: Int,
    val batch: String,
    val expiry: CodeExpiry = CodeExpiry.Default,
    val businessName: String? = null,
    val note: String? = null,
    val plan: String = "solo",
) {
    companion object {
        /** The longest a code may give: the database's check on activation_codes.days, which codes-admin applies too. */
        const val MAX_DAYS = 1100
    }
}

/** A code just made: shown this once, since the server keeps only its fingerprint. */
data class NewCode(val code: String, val expiresOn: LocalDate?)

/** What became of a code, as private.code_status tells it. */
data class CodeState(
    /** unused, key, renewal, expired, revoked; unknown or malformed when there is no such code. */
    val status: String,
    val batch: String? = null,
    val plan: String? = null,
    val days: Int? = null,
    val expiresOn: LocalDate? = null,
    val businessName: String? = null,
    val redeemedAt: Instant? = null,
    val revokedAt: Instant? = null,
    val note: String? = null,
)

/** A batch opened from « Lots » (private.batch_details): its counts and what its codes became. */
data class BatchDetails(
    val batch: CodeBatch,
    /** The day its free codes must be activated by; null when they never expire. */
    val expiresOn: LocalDate?,
    /** The company its codes open, when it was made for one client. */
    val businessName: String?,
    val note: String?,
    /** Codes that are the key of a business that still exists: the batch can't be deleted while there are any. */
    val liveKeys: Int,
    /** Its used codes, latest first. */
    val uses: List<BatchUse>,
)

/** A used code: the business it went to (null once that business was deleted), as its key or a renewal, and when. */
data class BatchUse(val key: Boolean, val businessName: String?, val redeemedAt: Instant?)

/** What « Supprimer le lot » did. */
sealed interface BatchDeletion {
    data class Deleted(val codes: Int) : BatchDeletion

    /** Refused: [keys] of its codes still open a business. */
    data class InUse(val keys: Int) : BatchDeletion

    /** No such batch any more. */
    data object Unknown : BatchDeletion
}

/** A client's business, as « Comptes clients » lists it. */
data class ClientBusiness(
    val id: String,
    val name: String,
    val contactPhone: String?,
    val createdOn: LocalDate?,
    val plan: String?,
    /** active, suspended or canceled. */
    val status: String?,
    val validFrom: LocalDate?,
    val validTo: LocalDate?,
    /** Phones holding its seat now. */
    val phones: Int,
    val lastSeen: Instant?,
    /** The owner's e-mail; null for an account made by a code. */
    val ownerEmail: String?,
    /** A code is its key. */
    val byCode: Boolean,
)

/** What became of a suspension, a reactivation or a deletion. */
enum class ClientOutcome { DONE, OWN_BUSINESS, UNKNOWN }

sealed interface AdminAnswer<out T> {
    data class Done<T>(val value: T) : AdminAnswer<T>

    /** This account is not DistriGo's owner. */
    data object NotAllowed : AdminAnswer<Nothing>
    data class Failed(val message: String) : AdminAnswer<Nothing>
}

/**
 * The owner's « Administration » screen and the server (codes-admin, docs/activation_codes.md step C4): the batches,
 * a new batch, one code's state, a code revoked. The server checks on every call that the account is the owner's.
 */
class CodesAdminClient(http: OkHttpClient, backend: Backend, auth: AuthRepository) {

    private val functions = FunctionCalls(http, backend, auth)

    suspend fun batches(): AdminAnswer<List<CodeBatch>> =
        ask(JsonObject().apply { addProperty("action", "batches") }, "ok") { reply ->
            reply.getAsJsonArray("batches")?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.let(::codeBatch) }.orEmpty()
        }

    // ── A batch, named as « Lots » lists it ──

    /** Its details; null when there is no such batch any more. */
    suspend fun batch(batch: CodeBatch): AdminAnswer<BatchDetails?> =
        ask(batchBody("batch", batch), null) { reply ->
            val read = reply.takeIf { reply.text("status") == "ok" }?.let(::codeBatch) ?: return@ask null
            BatchDetails(
                batch = read,
                expiresOn = reply.text("expires_on")?.let(::day),
                businessName = reply.text("business_name"),
                note = reply.text("note"),
                liveKeys = reply.int("live_keys") ?: 0,
                uses = reply.getAsJsonArray("uses")?.mapNotNull { element ->
                    val u = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    BatchUse(
                        key = u.text("use") == "key",
                        businessName = u.text("business_name"),
                        redeemedAt = u.text("redeemed_at")?.let(::instant),
                    )
                }.orEmpty(),
            )
        }

    /** « Révoquer les codes libres »: how many were revoked; null when there is no such batch any more. */
    suspend fun revokeBatch(batch: CodeBatch, reason: String?): AdminAnswer<Int?> {
        val body = batchBody("revoke_batch", batch).apply { reason?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("reason", it) } }
        return ask(body, null) { reply -> reply.int("revoked").takeIf { reply.text("status") == "ok" } }
    }

    /** « Supprimer le lot »: refused while one of its codes opens a business. */
    suspend fun deleteBatch(batch: CodeBatch): AdminAnswer<BatchDeletion> =
        ask(batchBody("delete_batch", batch), null) { reply ->
            when (reply.text("status")) {
                "deleted" -> BatchDeletion.Deleted(reply.int("codes") ?: 0)
                "in_use" -> BatchDeletion.InUse(reply.int("keys") ?: 0)
                else -> BatchDeletion.Unknown
            }
        }

    private fun batchBody(action: String, batch: CodeBatch) = JsonObject().apply {
        addProperty("action", action)
        addProperty("batch", batch.name)
        addProperty("plan", batch.plan)
        addProperty("days", batch.days)
    }

    private fun codeBatch(b: JsonObject): CodeBatch? {
        val name = b.text("batch") ?: return null
        return CodeBatch(
            name = name,
            plan = b.text("plan") ?: "solo",
            days = b.int("days") ?: 0,
            total = b.int("total") ?: 0,
            unused = b.int("unused") ?: 0,
            keys = b.int("keys") ?: 0,
            renewals = b.int("renewals") ?: 0,
            expired = b.int("expired") ?: 0,
            revoked = b.int("revoked") ?: 0,
            createdOn = b.text("created_on")?.let(::day),
        )
    }

    suspend fun create(order: CodeOrder): AdminAnswer<List<NewCode>> {
        val body = JsonObject().apply {
            addProperty("action", "create")
            addProperty("count", order.count)
            addProperty("plan", order.plan)
            addProperty("days", order.days)
            addProperty("batch", order.batch.trim())
            order.businessName?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("business_name", it) }
            order.note?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("note", it) }
            when (val expiry = order.expiry) {
                CodeExpiry.Default -> Unit
                CodeExpiry.Never -> add("expires_on", JsonNull.INSTANCE)
                is CodeExpiry.On -> addProperty("expires_on", expiry.day.toString())
            }
        }
        return ask(body, "created") { reply ->
            reply.getAsJsonArray("codes")?.mapNotNull { element ->
                val c = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                NewCode(c.text("code") ?: return@mapNotNull null, c.text("expires_on")?.let(::day))
            }.orEmpty()
        }
    }

    suspend fun status(code: String): AdminAnswer<CodeState> {
        val body = JsonObject().apply {
            addProperty("action", "status")
            addProperty("code", code)
        }
        return ask(body, null) { reply ->
            CodeState(
                status = reply.text("status") ?: "unknown",
                batch = reply.text("batch"),
                plan = reply.text("plan"),
                days = reply.int("days"),
                expiresOn = reply.text("expires_on")?.let(::day),
                businessName = reply.text("business_name"),
                redeemedAt = reply.text("redeemed_at")?.let(::instant),
                revokedAt = reply.text("revoked_at")?.let(::instant),
                note = reply.text("note"),
            )
        }
    }

    /** True when it was revoked (or already was); false when there is no such code. */
    suspend fun revoke(code: String, reason: String?): AdminAnswer<Boolean> {
        val body = JsonObject().apply {
            addProperty("action", "revoke")
            addProperty("code", code)
            reason?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("reason", it) }
        }
        return ask(body, null) { reply -> reply.text("status") == "revoked" }
    }

    // ── « Comptes clients » ──

    /** The businesses matching [query] (a name, a phone, an e-mail, a code); the latest without one. */
    suspend fun businesses(query: String?): AdminAnswer<List<ClientBusiness>> {
        val body = JsonObject().apply {
            addProperty("action", "businesses")
            query?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("query", it) }
        }
        return ask(body, "ok") { reply ->
            reply.getAsJsonArray("businesses")?.mapNotNull { element ->
                val b = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                ClientBusiness(
                    id = b.text("business_id") ?: return@mapNotNull null,
                    name = b.text("name") ?: return@mapNotNull null,
                    contactPhone = b.text("contact_phone"),
                    createdOn = b.text("created_on")?.let(::day),
                    plan = b.text("plan"),
                    status = b.text("status"),
                    validFrom = b.text("valid_from")?.let(::day),
                    validTo = b.text("valid_to")?.let(::day),
                    phones = b.int("phones") ?: 0,
                    lastSeen = b.text("last_seen")?.let(::instant),
                    ownerEmail = b.text("owner_email"),
                    byCode = b.get("by_code")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
                )
            }.orEmpty()
        }
    }

    /** Suspends (no license is issued any more) or reactivates a client's subscription. */
    suspend fun setSuspended(businessId: String, suspended: Boolean, note: String?): AdminAnswer<ClientOutcome> {
        val body = JsonObject().apply {
            addProperty("action", if (suspended) "suspend" else "reactivate")
            addProperty("business_id", businessId)
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("note", it) }
        }
        return ask(body, null) { reply -> outcome(reply.text("status"), done = "ok") }
    }

    /** Deletes a client's business, its subscription and its phones' seats. */
    suspend fun deleteBusiness(businessId: String): AdminAnswer<ClientOutcome> {
        val body = JsonObject().apply {
            addProperty("action", "delete_business")
            addProperty("business_id", businessId)
        }
        return ask(body, null) { reply -> outcome(reply.text("status"), done = "deleted") }
    }

    private fun outcome(status: String?, done: String) = when (status) {
        done -> ClientOutcome.DONE
        "own_business" -> ClientOutcome.OWN_BUSINESS
        else -> ClientOutcome.UNKNOWN
    }

    /** One call: not_allowed for anyone but the owner; [expected] (when given) is the status that carries the answer. */
    private suspend fun <T> ask(body: JsonObject, expected: String?, read: (JsonObject) -> T): AdminAnswer<T> {
        val reply = when (val answer = functions.call("codes-admin", body)) {
            is Reply.Answer -> answer.body
            is Reply.Refused -> return AdminAnswer.Failed(answer.message)
        }
        val status = reply.text("status")
        return when {
            status == "not_allowed" -> AdminAnswer.NotAllowed
            expected != null && status != expected -> AdminAnswer.Failed(FunctionCalls.SERVER_MESSAGE)
            else -> AdminAnswer.Done(read(reply))
        }
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(key: String): Int? = get(key)?.takeIf { it.isJsonPrimitive }?.asInt

    private fun day(text: String): LocalDate? = runCatching { LocalDate.parse(text.take(10)) }.getOrNull()

    private fun instant(text: String): Instant? = runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
}
