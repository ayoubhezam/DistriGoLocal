package com.distrigo.app.data.license

import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.AppMetaEntity

/**
 * The business the data on this phone belongs to (docs/license_l4_checkin.md §7): set by the first license
 * installed over it, and compared with every license after. Blocking: off the main thread.
 */
interface DataBusiness {
    /** Null until a license has bound the data. */
    fun read(): String?

    fun bind(businessId: String)
}

/**
 * `app_meta.business_id`: in the database, so it travels with the data into a `.distrigo` backup and back out of a
 * restore, and another business's backup is recognised as such.
 */
class AppMetaDataBusiness(private val db: () -> AppDatabase) : DataBusiness {

    override fun read(): String? = db().appMetaDao().get(KEY)?.takeIf { it.isNotBlank() }

    override fun bind(businessId: String) {
        db().appMetaDao().putAll(listOf(AppMetaEntity(KEY, businessId)))
    }

    companion object {
        const val KEY = "business_id"
    }
}
