package com.distrigo.app.data.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Who asked for a check-in (docs/license_l4_checkin.md §4). */
enum class CheckInSource { BACKGROUND, FOREGROUND }

/**
 * The last check-ins, in `no_backup/license/check_in.json`: when one was last tried and by whom, what came of it,
 * and when the server last answered, whoever asked and from the background job. The app-in-front rule and the
 * battery hint (L4c) read it, and so does whoever looks into a phone that stopped checking in.
 *
 * Plain JSON: nothing here opens anything, and an edit only changes when the phone next checks in.
 */
class CheckInLog(private val dir: File) {

    data class Entry(
        val lastAttemptAt: Long? = null,
        val lastSource: CheckInSource? = null,
        /** done, suspended, revoked, not_activated, failed (for good) or retry (the network or the server failed). */
        val lastOutcome: String? = null,
        /** What a failed check-in said, as the user would read it. */
        val lastError: String? = null,
        /** When the server last answered with a license, a suspension or a revocation. */
        val lastAnswerAt: Long? = null,
        val lastBackgroundAttemptAt: Long? = null,
        val lastBackgroundAnswerAt: Long? = null,
        /** Since when the 12-hour schedule has existed; null while there is none. */
        val scheduledSince: Long? = null,
    )

    private val file get() = File(dir, "check_in.json")

    /** File I/O: off the main thread. An unreadable log reads as empty. */
    fun read(): Entry = synchronized(LOCK) {
        try {
            if (file.isFile) fromJson(JsonParser().parse(file.readText(Charsets.UTF_8)).asJsonObject) else Entry()
        } catch (e: Exception) {
            Entry()
        }
    }

    /** One check-in, asked by [source], that ended in [result] at [at]. */
    fun record(source: CheckInSource, result: CheckIn, at: Long): Entry = synchronized(LOCK) {
        val before = read()
        val answered = result == CheckIn.Done || result == CheckIn.Suspended || result == CheckIn.Revoked
        val background = source == CheckInSource.BACKGROUND
        val entry = before.copy(
            lastAttemptAt = at,
            lastSource = source,
            lastOutcome = outcome(result),
            lastError = (result as? CheckIn.Failed)?.message,
            lastAnswerAt = if (answered) at else before.lastAnswerAt,
            lastBackgroundAttemptAt = if (background) at else before.lastBackgroundAttemptAt,
            lastBackgroundAnswerAt = if (background && answered) at else before.lastBackgroundAnswerAt,
        )
        write(entry)
        entry
    }

    /** The schedule exists from [at], unless it already did: called at every start, it keeps the first time. */
    fun markScheduled(at: Long) {
        synchronized(LOCK) {
            val before = read()
            if (before.scheduledSince == null) write(before.copy(scheduledSince = at))
        }
    }

    fun markUnscheduled() {
        synchronized(LOCK) {
            val before = read()
            if (before.scheduledSince != null) write(before.copy(scheduledSince = null))
        }
    }

    private fun outcome(result: CheckIn): String = when (result) {
        CheckIn.Done -> "done"
        CheckIn.Suspended -> "suspended"
        CheckIn.Revoked -> "revoked"
        CheckIn.NotActivated -> "not_activated"
        is CheckIn.Failed -> if (result.retry) "retry" else "failed"
    }

    /** Written whole and moved into place: a kill mid-write keeps the previous one. */
    private fun write(entry: Entry) {
        dir.mkdirs()
        val json = JsonObject().apply {
            addProperty("v", FORMAT)
            addProperty("last_attempt_at", entry.lastAttemptAt)
            addProperty("last_source", entry.lastSource?.name)
            addProperty("last_outcome", entry.lastOutcome)
            addProperty("last_error", entry.lastError)
            addProperty("last_answer_at", entry.lastAnswerAt)
            addProperty("last_background_attempt_at", entry.lastBackgroundAttemptAt)
            addProperty("last_background_answer_at", entry.lastBackgroundAnswerAt)
            addProperty("scheduled_since", entry.scheduledSince)
        }
        val temp = File(dir, "check_in.json.tmp")
        temp.writeText(json.toString(), Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun fromJson(json: JsonObject): Entry {
        if (json.get("v")?.asInt != FORMAT) return Entry()
        fun text(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        fun time(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asLong
        return Entry(
            lastAttemptAt = time("last_attempt_at"),
            lastSource = text("last_source")?.let { name -> CheckInSource.entries.firstOrNull { it.name == name } },
            lastOutcome = text("last_outcome"),
            lastError = text("last_error"),
            lastAnswerAt = time("last_answer_at"),
            lastBackgroundAttemptAt = time("last_background_attempt_at"),
            lastBackgroundAnswerAt = time("last_background_answer_at"),
            scheduledSince = time("scheduled_since"),
        )
    }

    companion object {
        private val LOCK = Any()
        private const val FORMAT = 1

        /** §3.4: how long the background may stay silent while other check-ins get through. */
        const val HELD_BACK_AFTER = 3 * 24 * 3_600_000L

        /**
         * The battery hint (§3.4, decision 6): no background check-in has got an answer for [HELD_BACK_AFTER] since
         * the schedule began or the last one did, though the phone has been online since (a check-in in front got
         * one). Android, or Samsung's battery saving, is holding the job back.
         */
        fun backgroundHeldBack(entry: Entry, now: Long): Boolean {
            val since = entry.lastBackgroundAnswerAt ?: entry.scheduledSince ?: return false
            val lastAnswer = entry.lastAnswerAt ?: return false
            return now - since > HELD_BACK_AFTER && lastAnswer > since
        }

        fun forApp(context: android.content.Context) =
            CheckInLog(File(context.applicationContext.noBackupFilesDir, "license"))
    }
}
