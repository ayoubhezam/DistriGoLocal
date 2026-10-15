package com.distrigo.app.data.backup

import android.database.sqlite.SQLiteDatabase

/**
 * The business a backup's data belongs to (`app_meta.business_id`, docs/license_l4_checkin.md §7) and the name it
 * carries, read once the backup is unpacked and before anything on the phone changes. A backup made before its data
 * was bound has no business: it takes the license's at the next check-in, like data never bound.
 */
data class RestoreBusiness(val businessId: String?, val name: String?) {

    /** Whether restoring it puts another business's data under the license of [licenseBusinessId]. */
    fun isOther(licenseBusinessId: String?): Boolean =
        businessId != null && licenseBusinessId != null && businessId != licenseBusinessId

    /** The dialog's paragraph: what happens after the restore. */
    fun line(): String =
        "Cette sauvegarde appartient à une autre entreprise" + (name?.let { " (« $it »)" } ?: "") + " que celle du compte connecté. " +
            "Après la restauration, DistriGo vous demandera de garder ces données pour votre entreprise, ou de vous déconnecter."

    companion object {
        /** Reads [prepared]'s database, already migrated to this app's version. Opened read-only. */
        fun read(prepared: PreparedRestore): RestoreBusiness {
            val database = SQLiteDatabase.openDatabase(prepared.database.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val id = database.rawQuery("SELECT value FROM app_meta WHERE key = 'business_id'", null).use {
                    if (it.moveToFirst()) it.getString(0)?.takeIf { value -> value.isNotBlank() } else null
                }
                val name = database.rawQuery("SELECT business_name FROM business_settings WHERE id = 1", null).use {
                    if (it.moveToFirst()) it.getString(0)?.takeIf { value -> value.isNotBlank() } else null
                }
                return RestoreBusiness(id, name)
            } finally {
                database.close()
            }
        }
    }
}
