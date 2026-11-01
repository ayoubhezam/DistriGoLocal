package com.distrigo.app.data.license

import com.distrigo.app.data.license.licText
import com.distrigo.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the license means for the screens (docs/license_l5_enforcement.md §1, §2, §8.1): whether a write may start,
 * the banner, and what the « Consultation seule » sheet says and offers. Like StockPolicy, it only shapes the
 * controls; the rule is the database's write gate (LicenseWriteGate).
 *
 * Pure, from a [LicenseState]: every state is checked off the phone.
 */
data class LicensePolicy(
    /** New documents and catalogue changes may be recorded. */
    val canRecord: Boolean,
    /** The line under the top bar; null when there is nothing to say. */
    val banner: Banner? = null,
    /** Why a write is refused, for the sheet; null when nothing is refused, or before the license is read. */
    val block: Block? = null,
) {
    /** D4: the open tournée can always be closed and the truck unloaded, whatever the license. */
    val canCloseTournee: Boolean get() = true
    val canUnloadTruck: Boolean get() = true

    /** Amber for a warning, red when recording stopped or is about to. */
    enum class Tone { WARNING, READ_ONLY }

    /** Where a banner's or a sheet's button leads. */
    enum class Action(@androidx.annotation.StringRes private val labelId: Int) {
        /** The « Abonnement » screen (§8.2). */
        SUBSCRIPTION(R.string.lic_action_subscription),
        /** Compte: sign in, keep the data, see the device. */
        ACCOUNT(R.string.lic_action_account),
        /** A check-in now. */
        RETRY(R.string.lic_action_retry),
        /** The phone's date settings. */
        DATE_SETTINGS(R.string.lic_action_date);

        val label: String get() = licText(labelId)
    }

    data class Banner(
        val text: String,
        val tone: Tone,
        val action: Action? = null,
        /** Decision 5: a warning closes for the day; a read-only banner stays. */
        val dismissible: Boolean = false,
        /** A second line, under [text]: what to do. */
        val detail: String? = null,
        /** Drawn as a full-width card rather than a line (§4, the phone's date set back). */
        val card: Boolean = false,
    )

    /** The « Consultation seule » sheet: one line of reason, then the main button. */
    data class Block(val reason: String, val action: Action, val actionLabel: String)

    companion object {
        /** Before the first evaluation: nothing to say, and no sheet, since nothing is known to be refused yet. */
        val UNCHECKED = LicensePolicy(canRecord = false)

        /** What still works, under the sheet's reason. */
        val STILL_WORKS: String get() = licText(R.string.lic_still_works)

        private val REACTIVATE: String get() = licText(R.string.lic_reactivate)

        fun of(state: LicenseState, zone: ZoneId = ZoneId.systemDefault()): LicensePolicy {
            fun date(millis: Long) = DAY_YEAR.withZone(zone).format(Instant.ofEpochMilli(millis))
            fun dayMonth(millis: Long) = DAY_MONTH.withZone(zone).format(Instant.ofEpochMilli(millis))
            val clockBanner = Banner(licText(R.string.lic_clock_banner), Tone.WARNING, Action.DATE_SETTINGS)

            return when (state) {
                LicenseState.Unchecked -> UNCHECKED
                is LicenseState.Active ->
                    LicensePolicy(true, clockBanner.takeIf { state.clockWarning })
                is LicenseState.RenewSoon -> LicensePolicy(
                    true,
                    Banner(licText(R.string.lic_expires_on, date(state.license.validTo)), Tone.WARNING, Action.SUBSCRIPTION, dismissible = true),
                )
                is LicenseState.CheckInSoon -> LicensePolicy(
                    true,
                    Banner(
                        licText(R.string.lic_connect_before, dayMonth(state.license.offlineUntil)),
                        Tone.WARNING, dismissible = true,
                    ),
                )
                is LicenseState.Grace -> LicensePolicy(
                    true,
                    Banner(
                        licText(R.string.lic_grace_banner, dayMonth(state.license.validTo), dayMonth(state.license.graceUntil)),
                        Tone.READ_ONLY, Action.SUBSCRIPTION,
                    ),
                )
                is LicenseState.Expired -> if (state.certain) {
                    readOnly(licText(R.string.lic_expired_banner), Action.SUBSCRIPTION,
                        licText(R.string.lic_expired_on, date(state.license.validTo)), REACTIVATE)
                } else {
                    // Only the phone's date says so, and it may be wrong (LicenseState.Expired.certain)
                    readOnly(licText(R.string.lic_expired_by_clock_banner), Action.RETRY,
                        licText(R.string.lic_expired_by_clock), licText(R.string.lic_action_retry))
                }
                is LicenseState.CheckInRequired ->
                    readOnly(licText(R.string.lic_check_needed_banner), Action.RETRY,
                        licText(R.string.lic_check_needed), licText(R.string.lic_action_retry))
                // §4: its own card, since every new document would carry a wrong date whatever the license says
                is LicenseState.ClockBehind -> LicensePolicy(
                    canRecord = false,
                    banner = Banner(
                        licText(R.string.lic_clock_title), Tone.READ_ONLY, Action.DATE_SETTINGS,
                        detail = licText(R.string.lic_clock_detail), card = true,
                    ),
                    block = Block(licText(R.string.lic_clock_block), Action.DATE_SETTINGS, licText(R.string.lic_action_date)),
                )
                LicenseState.Revoked ->
                    readOnly(licText(R.string.lic_revoked), Action.ACCOUNT,
                        licText(R.string.lic_revoked), licText(R.string.lic_action_account))
                is LicenseState.Suspended ->
                    readOnly(licText(R.string.lic_suspended), Action.SUBSCRIPTION,
                        licText(R.string.lic_suspended_short), REACTIVATE)
                is LicenseState.OtherBusiness ->
                    readOnly(licText(R.string.lic_other_business_banner), Action.ACCOUNT,
                        licText(R.string.lic_other_business), licText(R.string.lic_action_account))
                is LicenseState.NoLicense ->
                    readOnly(licText(R.string.lic_no_license_banner), Action.ACCOUNT,
                        licText(R.string.lic_no_license), licText(R.string.lic_action_account))
                is LicenseState.NotYetValid ->
                    readOnly(licText(R.string.lic_starts_on, date(state.license.validFrom)), Action.ACCOUNT,
                        licText(R.string.lic_starts_on, date(state.license.validFrom)), licText(R.string.lic_action_account))
            }
        }

        private fun readOnly(banner: String, action: Action, reason: String, actionLabel: String) = LicensePolicy(
            canRecord = false,
            banner = Banner(banner, Tone.READ_ONLY, action),
            block = Block(reason, action, actionLabel),
        )

        private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
    }
}
