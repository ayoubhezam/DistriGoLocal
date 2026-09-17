package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.distrigo.app.data.model.defaultTypeUuid

/**
 * The business the receipts are printed for: its name, phone and logo. One row, `id = 1`.
 *
 * It lived in SharedPreferences and a loose `business_logo.jpg`, beside the database rather than in it,
 * so nothing that copies the database — a backup, a restore, a sync — carried the identity every
 * receipt prints. As a row it goes wherever the data goes, and it is tracked like any other business
 * row: `updated_at`, `version` and `origin_device_id` move with its edits.
 *
 * Every phone gives it the same [BUSINESS_SETTINGS_UUID], so a company's phones hold one settings row
 * between them rather than one each.
 *
 * The logo is an ImageStore reference (`img:<sha256>`), like every product, client and supplier photo.
 */
@Entity(tableName = "business_settings", indices = [Index(value = ["uuid"], unique = true)])
data class BusinessSettingsEntity(
    @PrimaryKey
    val id: Int = ROW_ID,
    /** Null or blank: never set; receipts print "DISTRIGO". */
    val business_name: String?,
    val business_phone: String?,
    val logo_ref: String?,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = BUSINESS_SETTINGS_UUID,
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row. */
    val origin_device_id: String? = null,
) {
    companion object {
        const val ROW_ID = 1
    }
}

/** The one settings row's identity, the same on every phone — see defaultTypeUuid. Never change it. */
val BUSINESS_SETTINGS_UUID: String = defaultTypeUuid("settings", "business")
