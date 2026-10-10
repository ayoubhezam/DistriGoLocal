package com.distrigo.app.data.license

/** Why only a check-in can tell what the license allows. */
enum class CheckInReason {
    /** Past the license's `offlineUntil`. */
    OFFLINE_TOO_LONG,
    /** More reboots since the last check-in than the license allows. */
    TOO_MANY_REBOOTS,
    /** The anchor state is missing or was altered, so the time cannot be vouched for. */
    CLOCK_STATE_LOST,
    /** The Keystore or the files could not be read. */
    UNREADABLE,
}

/**
 * What the license allows right now: docs/license_architecture.md §2.5.
 *
 * Only [canRecord] decides anything. The rest is for the screens: which banner, which dates.
 */
sealed interface LicenseState {

    /** Whether new documents may be recorded. */
    val canRecord: Boolean

    /** Before the first evaluation. */
    data object Unchecked : LicenseState {
        override val canRecord get() = false
    }

    /** No license for this phone. [rejection] says what was wrong with the stored one; null when there is none. */
    data class NoLicense(val rejection: Rejection? = null) : LicenseState {
        override val canRecord get() = false
    }

    /** The server said this phone no longer holds the subscription: transferred or released. */
    data object Revoked : LicenseState {
        override val canRecord get() = false
    }

    /** Only a check-in can tell. */
    data class CheckInRequired(val license: License?, val why: CheckInReason, val error: Throwable? = null) : LicenseState {
        override val canRecord get() = false
    }

    /** A license judged at a time the phone can vouch for. */
    sealed interface Judged : LicenseState {
        val license: License
        val time: TrustedTime.Known

        /** The phone's date is behind, not enough to stop recording (§2.5): an amber banner. */
        val clockWarning: Boolean get() = time.behindBy > LicenseRules.CLOCK_WARNING
    }

    data class Active(override val license: License, override val time: TrustedTime.Known) : Judged {
        override val canRecord get() = true
    }

    /** Less than [LicenseRules.RENEW_SOON] before the end of the subscription. */
    data class RenewSoon(override val license: License, override val time: TrustedTime.Known) : Judged {
        override val canRecord get() = true
    }

    /** Less than [LicenseRules.CHECK_IN_SOON] before the phone must check in. */
    data class CheckInSoon(override val license: License, override val time: TrustedTime.Known) : Judged {
        override val canRecord get() = true
    }

    /** Past the end of the subscription, within the grace days: recording continues under a red banner. */
    data class Grace(override val license: License, override val time: TrustedTime.Known) : Judged {
        override val canRecord get() = true
    }

    data class Expired(override val license: License, override val time: TrustedTime.Known) : Judged {
        override val canRecord get() = false

        /**
         * Whether the time the phone can prove is past the grace; if not, only the phone's date says so, and
         * that date may be wrong: "check the date, or connect" rather than "expired".
         */
        val certain: Boolean get() = time.floor > license.graceUntil
    }

    /** The phone's date is more than [LicenseRules.CLOCK_BLOCK] behind: every new document would be misdated. */
    data class ClockBehind(override val license: License, override val time: TrustedTime.Known) : Judged {
        override val canRecord get() = false
    }

    /** Before the subscription starts. */
    data class NotYetValid(override val license: License, override val time: TrustedTime.Known) : Judged {
        override val canRecord get() = false
    }
}

/** What a license allows at a given time. Pure, so every boundary is tested off the phone. */
object LicenseRules {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    const val RENEW_SOON = 7 * DAY
    const val CHECK_IN_SOON = 3 * DAY
    const val CLOCK_WARNING = 10 * MINUTE
    const val CLOCK_BLOCK = 24 * HOUR

    /**
     * In order of precedence: an expired subscription says so even when the phone also has to check in, and a
     * date set back is named as such rather than as a check-in the user cannot make sense of.
     */
    fun judge(license: License, time: TrustedTime): LicenseState {
        if (time !is TrustedTime.Known) return LicenseState.CheckInRequired(license, CheckInReason.CLOCK_STATE_LOST)
        val now = time.effective
        return when {
            now > license.graceUntil -> LicenseState.Expired(license, time)
            time.behindBy > CLOCK_BLOCK -> LicenseState.ClockBehind(license, time)
            now > license.offlineUntil -> LicenseState.CheckInRequired(license, CheckInReason.OFFLINE_TOO_LONG)
            time.rebootsSinceCheckIn > license.maxReboots -> LicenseState.CheckInRequired(license, CheckInReason.TOO_MANY_REBOOTS)
            now < license.validFrom -> LicenseState.NotYetValid(license, time)
            now > license.validTo -> LicenseState.Grace(license, time)
            license.offlineUntil - now < CHECK_IN_SOON -> LicenseState.CheckInSoon(license, time)
            license.validTo - now < RENEW_SOON -> LicenseState.RenewSoon(license, time)
            else -> LicenseState.Active(license, time)
        }
    }
}
