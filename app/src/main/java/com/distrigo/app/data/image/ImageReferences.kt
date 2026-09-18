package com.distrigo.app.data.image

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Where the database points at stored photos. A photo file is named by its content, so two rows that hold the
 * same picture share one file: a file may only go when no row anywhere refers to it any more.
 */
object ImageReferences {

    /** The `img:` references in a value, including inside a draft's JSON. */
    private val REF = Regex("${ImageStore.REF_PREFIX}([0-9a-f]{64})")

    fun hashesIn(value: String?): List<String> = value?.let { REF.findAll(it).map { m -> m.groupValues[1] }.toList() }.orEmpty()

    /** Every photo hash any text column of any table still refers to. Scans the whole database: for rare use. */
    fun referencedHashes(db: SupportSQLiteDatabase): Set<String> {
        val referenced = mutableSetOf<String>()
        for (table in userTables(db)) {
            for (column in textColumns(db, table)) {
                db.query("SELECT `$column` FROM `$table` WHERE instr(`$column`, '${ImageStore.REF_PREFIX}') > 0").use { cursor ->
                    while (cursor.moveToNext()) referenced += hashesIn(cursor.getString(0))
                }
            }
        }
        return referenced
    }

    private fun userTables(db: SupportSQLiteDatabase): List<String> =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' " +
                "AND name NOT LIKE 'room\\_%' ESCAPE '\\' AND name NOT LIKE 'android\\_%' ESCAPE '\\'"
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun textColumns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                val name = c.getColumnIndexOrThrow("name")
                val type = c.getColumnIndexOrThrow("type")
                while (c.moveToNext()) if (c.getString(type).uppercase().contains("TEXT")) add(c.getString(name))
            }
        }
}
