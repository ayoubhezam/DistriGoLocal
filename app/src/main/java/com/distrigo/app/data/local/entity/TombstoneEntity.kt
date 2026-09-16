package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A record that a business row was deleted for good: which table, which row, when.
 *
 * Master data — clients, suppliers, products, categories and the like — is never deleted this way;
 * it is marked with `deleted_at` and kept, so its history still has something to point at. Documents
 * and their lines are deleted for real, and once the row is gone its `uuid` is gone with it. This
 * table keeps that uuid, so a later backup merge or sync can tell "deleted here" from "never
 * existed here".
 *
 * Rows are written only by the `AFTER DELETE` trigger on each business table (see
 * ChangeTracking.kt), never by the app, which is why there is no DAO. That covers every delete path,
 * including the ones that are edits in disguise: editing a vente deletes and re-inserts its lines and
 * stock movements, and each replaced line leaves a tombstone.
 *
 * Nothing reads or prunes these yet. Pruning belongs with sync, which will know what has been sent.
 */
@Entity(
    tableName = "tombstones",
    indices = [Index(value = ["table_name", "row_uuid"], unique = true)]
)
data class TombstoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val table_name: String,
    val row_uuid: String,
    /** Milliseconds since the epoch, UTC, like `updated_at`. */
    val deleted_at: Long,
    /** The device that deleted the row; null for deletes before version 48. */
    val device_id: String? = null,
)
