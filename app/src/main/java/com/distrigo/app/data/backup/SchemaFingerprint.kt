package com.distrigo.app.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The shape of a database as the app depends on it: every table's columns and indices, and every trigger.
 *
 * Room checks a database's tables only after it migrates one. A backup already at this app's version is
 * opened on the strength of an identity hash stored inside the file, so a file edited by hand, with valid
 * checksums, could lack a column or carry a trigger of its own. Comparing its fingerprint with a database
 * this app has just created catches that before the file replaces the user's data.
 *
 * Columns compare as Room compares them between two databases: name, type affinity, NOT NULL and primary
 * key position, not default values. Triggers compare by name and SQL: the app installs its own on every
 * open, so after opening, a restored database holds exactly the new one's.
 */
data class SchemaFingerprint(
    val tables: Map<String, Table>,
    val views: Set<String>,
    val triggers: Map<String, String>,
) {
    data class Table(val columns: Map<String, Column>, val indices: Map<String, Index>)
    data class Column(val affinity: String, val notNull: Boolean, val primaryKeyPosition: Int)
    data class Index(val unique: Boolean, val columns: List<String>)

    /** What differs from [expected], in words for the log; empty when the two match. */
    fun differencesFrom(expected: SchemaFingerprint): List<String> = buildList {
        (expected.tables.keys - tables.keys).sorted().forEach { add("missing table $it") }
        (tables.keys - expected.tables.keys).sorted().forEach { add("extra table $it") }
        for ((name, want) in expected.tables.toSortedMap()) {
            val have = tables[name] ?: continue
            (want.columns.keys - have.columns.keys).sorted().forEach { add("missing column $name.$it") }
            (have.columns.keys - want.columns.keys).sorted().forEach { add("extra column $name.$it") }
            for ((column, spec) in want.columns) {
                val actual = have.columns[column] ?: continue
                if (actual != spec) add("column $name.$column is $actual, not $spec")
            }
            if (have.indices != want.indices) add("indices of $name differ")
        }
        if (views != expected.views) add("views differ")
        (expected.triggers.keys - triggers.keys).sorted().forEach { add("missing trigger $it") }
        (triggers.keys - expected.triggers.keys).sorted().forEach { add("extra trigger $it") }
        for ((name, sql) in expected.triggers) {
            if (triggers[name] != null && triggers[name] != sql) add("trigger $name differs")
        }
    }

    companion object {

        fun read(db: SupportSQLiteDatabase): SchemaFingerprint {
            val tables = names(db, "table").associateWith { table ->
                Table(columns(db, table), indices(db, table))
            }
            val triggers = db.query("SELECT name, sql FROM sqlite_master WHERE type = 'trigger'").use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
            }
            return SchemaFingerprint(tables, names(db, "view").toSet(), triggers)
        }

        private fun names(db: SupportSQLiteDatabase, type: String): List<String> =
            db.query(
                "SELECT name FROM sqlite_master WHERE type = ? AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' " +
                    "AND name NOT IN ('android_metadata', 'room_master_table')",
                arrayOf(type)
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

        private fun columns(db: SupportSQLiteDatabase, table: String): Map<String, Column> =
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                val type = cursor.getColumnIndexOrThrow("type")
                val notNull = cursor.getColumnIndexOrThrow("notnull")
                val pk = cursor.getColumnIndexOrThrow("pk")
                buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(name), Column(affinity(cursor.getString(type)), cursor.getInt(notNull) != 0, cursor.getInt(pk)))
                    }
                }
            }

        private fun indices(db: SupportSQLiteDatabase, table: String): Map<String, Index> {
            val list = db.query("PRAGMA index_list(`$table`)").use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                val unique = cursor.getColumnIndexOrThrow("unique")
                buildList { while (cursor.moveToNext()) add(cursor.getString(name) to (cursor.getInt(unique) != 0)) }
            }
            return list.filterNot { it.first.startsWith("sqlite_autoindex") }.associate { (index, unique) ->
                val columns = db.query("PRAGMA index_info(`$index`)").use { cursor ->
                    val seq = cursor.getColumnIndexOrThrow("seqno")
                    val name = cursor.getColumnIndexOrThrow("name")
                    buildList { while (cursor.moveToNext()) add(cursor.getInt(seq) to cursor.getString(name)) }
                }.sortedBy { it.first }.map { it.second }
                index to Index(unique, columns)
            }
        }

        /** SQLite's rules for a declared type, as Room applies them. */
        internal fun affinity(declared: String?): String {
            val type = declared.orEmpty().uppercase()
            return when {
                "INT" in type -> "INTEGER"
                "CHAR" in type || "CLOB" in type || "TEXT" in type -> "TEXT"
                "BLOB" in type || type.isEmpty() -> "BLOB"
                "REAL" in type || "FLOA" in type || "DOUB" in type -> "REAL"
                else -> "NUMERIC"
            }
        }
    }
}
