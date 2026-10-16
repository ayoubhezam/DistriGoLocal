package com.distrigo.app.ui.onboarding

import android.content.Context
import com.distrigo.app.data.auth.AccountState

/** Where the app opens, or where it goes once an account leaves this phone. */
sealed interface Entry {
    /** The app as it is: its data, in consultation while no license allows recording (L5). */
    data object App : Entry

    /** The onboarding at [start]; [required]: Back leaves the app rather than the onboarding. */
    data class Onboarding(val start: OnboardingStart, val required: Boolean) : Entry
}

/**
 * The welcome screen and its four pages are for someone who has never used DistriGo here (the owner's choice,
 * 2026-10-15). Whoever knows it goes to Connexion instead, and a phone holding data always opens on its data:
 * signing out promises « consultation seule », and backups and exports stay within reach. Signed out, Compte itself
 * is the Connexion form.
 */
object EntryPolicy {

    /**
     * At launch, once the session is read: only a phone with no account, no license and nothing recorded asks for
     * one, with the tour the first time. [holdsLicense] and [hasData] are asked only when needed (the second opens
     * the database).
     */
    fun atLaunch(account: AccountState, holdsLicense: () -> Boolean, hasData: () -> Boolean, introSeen: () -> Boolean): Entry =
        when {
            account != AccountState.SignedOut || holdsLicense() || hasData() -> Entry.App
            introSeen() -> Entry.Onboarding(OnboardingStart.SIGN_IN, required = true)
            else -> Entry.Onboarding(OnboardingStart.WELCOME, required = true)
        }
}

/**
 * Whether the welcome screen and its pages were seen on this phone: left for a form, or an account signed in.
 * Plain preferences, which Android's own backup carries, so a restored phone has seen them too. Disk: off the main
 * thread.
 */
object OnboardingIntro {
    private const val PREFS = "onboarding"
    private const val SEEN = "intro_seen"

    fun seen(context: Context): Boolean = prefs(context).getBoolean(SEEN, false)

    fun markSeen(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(SEEN, false)) prefs.edit().putBoolean(SEEN, true).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
