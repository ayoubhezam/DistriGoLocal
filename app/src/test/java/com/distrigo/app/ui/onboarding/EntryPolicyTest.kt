package com.distrigo.app.ui.onboarding

import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the app opens (the owner's choice, 2026-10-15): the tour once, Connexion after, the data always. */
class EntryPolicyTest {

    private val signedIn = AccountState.SignedIn(session())
    private val never: () -> Boolean = { throw AssertionError("not asked: the answer was already known") }

    @Test
    fun `a phone never used opens on the welcome tour, and Back leaves the app`() {
        assertEquals(
            Entry.Onboarding(OnboardingStart.WELCOME, required = true),
            EntryPolicy.atLaunch(AccountState.SignedOut, holdsLicense = { false }, hasData = { false }, introSeen = { false }),
        )
    }

    @Test
    fun `whoever has seen the tour opens on Connexion instead`() {
        assertEquals(
            Entry.Onboarding(OnboardingStart.SIGN_IN, required = true),
            EntryPolicy.atLaunch(AccountState.SignedOut, holdsLicense = { false }, hasData = { false }, introSeen = { true }),
        )
    }

    @Test
    fun `a phone holding data opens on it, signed out or not, and the database isn't opened for a signed-in account`() {
        assertEquals(Entry.App, EntryPolicy.atLaunch(AccountState.SignedOut, holdsLicense = { false }, hasData = { true }, introSeen = { false }))
        assertEquals(Entry.App, EntryPolicy.atLaunch(AccountState.SignedOut, holdsLicense = { true }, hasData = never, introSeen = never))
        assertEquals(Entry.App, EntryPolicy.atLaunch(signedIn, holdsLicense = never, hasData = never, introSeen = never))
    }

    @Test
    fun `a sign-in made in Compte continues in the onboarding only for a step the onboarding shows`() {
        assertFalse(OnboardingStatus().followUp)
        assertFalse("still working: Compte's button turns", OnboardingStatus(busy = true).followUp)
        assertFalse("a wrong password is said under Compte's form", OnboardingStatus(error = "E-mail ou mot de passe incorrect.").followUp)
        assertTrue(OnboardingStatus(needsBusiness = true).followUp)
        assertTrue(OnboardingStatus(seatTaken = listOf("SM-A145F")).followUp)
        assertTrue(OnboardingStatus(codeRequired = true).followUp)
        assertTrue(OnboardingStatus(activation = ActivationSummary(ActivationOrigin.SIGN_IN, trial = true, validFromMillis = 0, validToMillis = 1)).followUp)
    }
}

/** The success screen's « Il reste … » under « Expire le ». */
class TimeLeftTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `the time left reads in days, then months or years, down to the last day`() {
        assertEquals("Il reste 25 jours", timeLeft(validToMillis = 25 * day + day / 2, now = 0))
        assertEquals("Il reste 1 jour", timeLeft(validToMillis = day + 1, now = 0))
        assertEquals("Dernier jour", timeLeft(validToMillis = day / 3, now = 0))
        assertEquals("Il reste 6 mois", timeLeft(validToMillis = 183 * day, now = 0))
        assertEquals("Il reste 1 an", timeLeft(validToMillis = 365 * day, now = 0))
    }
}
