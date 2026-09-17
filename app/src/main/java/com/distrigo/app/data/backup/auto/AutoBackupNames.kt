package com.distrigo.app.data.backup.auto

import com.distrigo.app.data.backup.BackupFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The names automatic backups are saved under, and the only names the app ever deletes.
 *
 * To the second, so two backups in one minute do not meet under one name: a documents provider would save the
 * second as `… (1).distrigo`, which rotation could no longer recognise as its own.
 */
object AutoBackupNames {

    private const val PREFIX = "DistriGo-auto-"
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    private val PATTERN = Regex("DistriGo-auto-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.${BackupFormat.EXTENSION}")

    /** `DistriGo-auto-2026-09-18-030015.distrigo`, in local time. */
    fun forTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        PREFIX + STAMP.format(at.atZone(zone)) + ".${BackupFormat.EXTENSION}"

    fun isAutomatic(name: String): Boolean = PATTERN.matches(name)

    /**
     * The files to delete so that the newest [keep] automatic backups remain. Anything that is not exactly an
     * automatic backup's name — a manual backup, a renamed copy, the user's own files — is never among them.
     */
    fun beyondNewest(names: List<String>, keep: Int): List<String> =
        names.filter(::isAutomatic).sortedDescending().drop(keep)
}
