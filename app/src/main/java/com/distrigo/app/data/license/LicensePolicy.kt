package com.distrigo.app.data.license

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
    enum class Action(val label: String) {
        /** The « Abonnement » screen (§8.2). */
        SUBSCRIPTION("Abonnement"),
        /** Compte: sign in, keep the data, see the device. */
        ACCOUNT("Compte"),
        /** A check-in now. */
        RETRY("Réessayer"),
        /** The phone's date settings. */
        DATE_SETTINGS("Régler la date"),
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
        const val STILL_WORKS = "Vos données restent consultables ; sauvegarde et export disponibles."

        private const val REACTIVATE = "Réactiver mon abonnement"

        fun of(state: LicenseState, zone: ZoneId = ZoneId.systemDefault()): LicensePolicy {
            fun date(millis: Long) = DAY_YEAR.withZone(zone).format(Instant.ofEpochMilli(millis))
            fun dayMonth(millis: Long) = DAY_MONTH.withZone(zone).format(Instant.ofEpochMilli(millis))
            val clockBanner = Banner("La date du téléphone semble en retard. Vérifiez « Date et heure automatiques ».", Tone.WARNING, Action.DATE_SETTINGS)

            return when (state) {
                LicenseState.Unchecked -> UNCHECKED
                is LicenseState.Active ->
                    LicensePolicy(true, clockBanner.takeIf { state.clockWarning })
                is LicenseState.RenewSoon -> LicensePolicy(
                    true,
                    Banner("Votre abonnement expire le ${date(state.license.validTo)}.", Tone.WARNING, Action.SUBSCRIPTION, dismissible = true),
                )
                is LicenseState.CheckInSoon -> LicensePolicy(
                    true,
                    Banner(
                        "Connectez-vous à Internet avant le ${dayMonth(state.license.offlineUntil)} pour continuer à enregistrer.",
                        Tone.WARNING, dismissible = true,
                    ),
                )
                is LicenseState.Grace -> LicensePolicy(
                    true,
                    Banner(
                        "Abonnement expiré le ${dayMonth(state.license.validTo)}. Période de grâce jusqu'au ${dayMonth(state.license.graceUntil)}.",
                        Tone.READ_ONLY, Action.SUBSCRIPTION,
                    ),
                )
                is LicenseState.Expired -> if (state.certain) {
                    readOnly("Abonnement expiré — consultation seule. Sauvegarde et export restent disponibles.", Action.SUBSCRIPTION,
                        "Votre abonnement a expiré le ${date(state.license.validTo)}.", REACTIVATE)
                } else {
                    // Only the phone's date says so, and it may be wrong (LicenseState.Expired.certain)
                    readOnly("Abonnement expiré selon la date du téléphone : vérifiez la date ou connectez-vous à Internet.", Action.RETRY,
                        "La date du téléphone dit que l'abonnement a expiré. Vérifiez-la, ou connectez-vous à Internet.", "Réessayer")
                }
                is LicenseState.CheckInRequired ->
                    readOnly("Vérification de l'abonnement nécessaire : connectez-vous à Internet.", Action.RETRY,
                        "L'abonnement doit être vérifié : connectez-vous à Internet.", "Réessayer")
                // §4: its own card, since every new document would carry a wrong date whatever the license says
                is LicenseState.ClockBehind -> LicensePolicy(
                    canRecord = false,
                    banner = Banner(
                        "La date du téléphone est en retard", Tone.READ_ONLY, Action.DATE_SETTINGS,
                        detail = "Activez « Date et heure automatiques », puis revenez.", card = true,
                    ),
                    block = Block("La date du téléphone est en retard : chaque document serait mal daté.", Action.DATE_SETTINGS, "Régler la date"),
                )
                LicenseState.Revoked ->
                    readOnly("Cet appareil n'est plus associé au compte.", Action.ACCOUNT,
                        "Cet appareil n'est plus associé au compte.", "Compte")
                is LicenseState.Suspended ->
                    readOnly("L'abonnement est suspendu. Contactez DistriGo.", Action.SUBSCRIPTION,
                        "L'abonnement est suspendu.", REACTIVATE)
                is LicenseState.OtherBusiness ->
                    readOnly("Ces données appartiennent à une autre entreprise.", Action.ACCOUNT,
                        "Ces données appartiennent à une autre entreprise que celle du compte.", "Compte")
                is LicenseState.NoLicense ->
                    readOnly("Connectez-vous à votre compte pour enregistrer.", Action.ACCOUNT,
                        "Aucun abonnement n'est actif sur ce téléphone.", "Compte")
                is LicenseState.NotYetValid ->
                    readOnly("L'abonnement commence le ${date(state.license.validFrom)}.", Action.ACCOUNT,
                        "L'abonnement commence le ${date(state.license.validFrom)}.", "Compte")
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
