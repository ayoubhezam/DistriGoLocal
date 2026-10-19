package com.distrigo.app.data.debug

import com.distrigo.app.BuildConfig
import com.distrigo.app.data.license.CheckInReason
import com.distrigo.app.data.license.LicenseState
import java.io.File

/**
 * **Debug builds only.** Draws the screens as another license state would (docs/license_l5_enforcement.md §2), so the
 * banner, the « Consultation seule » sheet and the guarded buttons can be looked at on the development phone without
 * touching its license. Only the screens change: the database's write gate keeps following the real license, so a
 * write a screen forgot to guard still goes through here. The real walk is L5d, on a test account.
 *
 *     adb shell run-as com.distrigo.app sh -c 'echo expired > /data/data/com.distrigo.app/files/license_preview'  # restart the app
 *     adb shell run-as com.distrigo.app rm /data/data/com.distrigo.app/files/license_preview
 *
 * Names: renew, checkin, grace, expired, checkin-required, clock, suspended, other (these need a license held), revoked, none.
 */
object LicensePreview {

    const val FILE = "license_preview"

    /** The state to draw instead of [real], or null when there is no preview (always in a release build). */
    fun read(filesDir: File, real: LicenseState): LicenseState? {
        if (!BuildConfig.DEBUG) return null
        val name = try {
            File(filesDir, FILE).takeIf { it.isFile }?.readText()?.trim()
        } catch (e: Exception) {
            null
        } ?: return null
        val judged = real as? LicenseState.Judged
        return when (name) {
            "revoked" -> LicenseState.Revoked
            "none" -> LicenseState.NoLicense()
            else -> judged?.let { s ->
                when (name) {
                    "renew" -> LicenseState.RenewSoon(s.license, s.time)
                    "checkin" -> LicenseState.CheckInSoon(s.license, s.time)
                    "grace" -> LicenseState.Grace(s.license, s.time)
                    "expired" -> LicenseState.Expired(s.license.copy(graceUntil = s.time.floor - 1), s.time)
                    "checkin-required" -> LicenseState.CheckInRequired(s.license, CheckInReason.OFFLINE_TOO_LONG)
                    "clock" -> LicenseState.ClockBehind(s.license, s.time)
                    "suspended" -> LicenseState.Suspended(s.license)
                    "other" -> LicenseState.OtherBusiness(s.license, "preview")
                    else -> null
                }
            }
        }
    }
}
