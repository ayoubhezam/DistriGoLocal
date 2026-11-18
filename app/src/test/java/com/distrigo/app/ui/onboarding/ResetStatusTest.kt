package com.distrigo.app.ui.onboarding

import com.distrigo.app.data.auth.AuthError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The reset page's step lives in [OnboardingStatus.resetSentTo]. Until 2026-11-18 any error rebuilt the status
 * without it, so a refused password threw the user back to the e-mail step and the next « Envoyer le code » hit
 * Supabase's one-a-minute limit (found on the phone, not by the tests, which only covered AuthRepository).
 */
class ResetStatusTest {

    private val previousLocale = java.util.Locale.getDefault()
    @Before fun french() { com.distrigo.app.testing.FrenchStrings.install(); java.util.Locale.setDefault(java.util.Locale.FRENCH) }
    @After fun restore() { com.distrigo.app.testing.FrenchStrings.uninstall(); java.util.Locale.setDefault(previousLocale) }

    private val onCodeStep = OnboardingStatus(resetSentTo = "a@example.com")

    @Test
    fun `a refused password keeps the code step, with its message`() {
        for (error in listOf(AuthError.SAME_PASSWORD, AuthError.WEAK_PASSWORD, AuthError.RESET_CODE_INVALID, AuthError.RESET_WAIT, AuthError.NETWORK)) {
            val after = failedStatus(onCodeStep.copy(busy = true), error.message)
            assertEquals(error.name, "a@example.com", after.resetSentTo)
            assertEquals(error.message, after.error)
            assertEquals(false, after.busy)
        }
    }

    @Test
    fun `an error keeps the company form too, and drops what the attempt had set`() {
        val after = failedStatus(OnboardingStatus(needsBusiness = true, seatTaken = listOf("M34"), codeRequired = true), "x")
        assertEquals(true, after.needsBusiness)
        assertNull(after.seatTaken)
        assertEquals(false, after.codeRequired)
        assertNull(after.resetSentTo)
    }

    @Test
    fun `a sent code opens the code step`() {
        val after = resetRequested(OnboardingStatus(error = "old"), "a@example.com", null)!!
        assertEquals("a@example.com", after.resetSentTo)
        assertNull(after.error)
    }

    @Test
    fun `a code asked for again within the minute opens the code step, saying one is on its way`() {
        val after = resetRequested(OnboardingStatus(), "a@example.com", AuthError.RESET_SENT_RECENTLY)!!
        assertEquals("a@example.com", after.resetSentTo)
        assertEquals(AuthError.RESET_SENT_RECENTLY.message, after.error)
    }

    @Test
    fun `any other refusal of the request is left to failedStatus`() {
        assertNull(resetRequested(OnboardingStatus(), "a@example", AuthError.INVALID_EMAIL))
        assertNull(resetRequested(OnboardingStatus(), "a@example.com", AuthError.NETWORK))
    }

    @Test
    fun `changing the address goes back to the e-mail step without the last error`() {
        val after = resetEmailChanged(onCodeStep.copy(error = "x"))
        assertNull(after.resetSentTo)
        assertNull(after.error)
    }
}
