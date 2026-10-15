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
 *  - `business_id` — the business the data belongs to, set by the first license installed over it (see
 *    data/license/DataBusiness.kt). A license of another business finds it and asks whose data this is.
 *  - `numbering.<table>` — the document number counters (see DocumentNumbers.kt).
 *  - `backup.last_at`, `backup.last_size`, `backup.last_name` — the last backup made of this database: when
 *    (a UTC instant), its size in bytes, and the file name it was saved as (see BackupCreator).
 *
 * A table rather than preferences because it has to be read inside triggers, and has to move with the
 * data it describes.
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)
