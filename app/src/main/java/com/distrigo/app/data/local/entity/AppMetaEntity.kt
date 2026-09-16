package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Facts about this database itself, as key and value. Kept in the database, so they travel with it
 * into a backup and back out of a restore.
 *
 * Keys in use (see DeviceTracking.kt):
 *
 *  - `database_id` — a UUID given to this database the first time it opened with this table. A backup
 *    restored elsewhere keeps it: it names the data, not the phone.
 *  - `device_id` — the installation that last opened the database (see DeviceIdentity). When it is not
 *    the current one, this database was copied here from another device. The triggers that stamp
 *    `origin_device_id` and `tombstones.device_id` read it.
 *
 * A table rather than preferences because it has to be read inside triggers, and has to move with the
 * data it describes. Per-device document counters and the last backup time will live here too.
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)
