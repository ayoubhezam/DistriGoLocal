package com.distrigo.app.data.license

import android.util.Log
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.LicenseWriteGate
import com.distrigo.app.data.local.entity.AppMetaEntity

/**
 * Where LicenseManager puts what the license allows, so the database refuses what it doesn't
 * (docs/license_l5_enforcement.md §3). Blocking: off the main thread.
 */
interface WriteGate {
    /** Called with every state LicenseManager publishes. */
    fun follow(canRecord: Boolean)
}

/** No database to gate: the license alone, as in the license's own tests. */
object NoWriteGate : WriteGate {
    override fun follow(canRecord: Boolean) = Unit
}

/**
 * `app_meta.license.write_gate` in the app's database. The value is also kept for the database's next open
 * ([LicenseWriteGate.licenseAllows]), which writes it before anything else can write.
 */
class AppMetaWriteGate(private val db: () -> AppDatabase) : WriteGate {

    /** What this process last wrote; the database is not written again for the same value. */
    @Volatile
    private var written: Boolean? = null

    override fun follow(canRecord: Boolean) {
        LicenseWriteGate.licenseAllows = canRecord
        if (written == canRecord) return
        try {
            db().appMetaDao().putAll(listOf(AppMetaEntity(LicenseWriteGate.KEY, if (canRecord) LicenseWriteGate.OPEN else LicenseWriteGate.CLOSED)))
            written = canRecord
        } catch (e: RuntimeException) {
            // The next state published, or the next open, writes it
            Log.w("AppMetaWriteGate", "could not write the license gate", e)
        }
    }
}
