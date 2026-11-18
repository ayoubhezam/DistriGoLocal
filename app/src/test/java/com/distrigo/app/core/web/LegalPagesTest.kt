package com.distrigo.app.core.web

import com.distrigo.app.core.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app's links to the legal pages on distrigoo.com: each page, in the app's language. */
class LegalPagesTest {

    @Test
    fun `each page in French is its address with the trailing slash the site serves`() {
        assertEquals("https://distrigoo.com/confidentialite/", LegalPages.url(LegalPage.PRIVACY, AppLanguage.FRENCH))
        assertEquals("https://distrigoo.com/conditions/", LegalPages.url(LegalPage.TERMS, AppLanguage.FRENCH))
        assertEquals("https://distrigoo.com/suppression-compte/", LegalPages.url(LegalPage.ACCOUNT_DELETION, AppLanguage.FRENCH))
    }

    @Test
    fun `in Arabic the same page opens in Arabic`() {
        assertEquals("https://distrigoo.com/confidentialite/?lang=ar", LegalPages.url(LegalPage.PRIVACY, AppLanguage.ARABIC))
        assertEquals("https://distrigoo.com/conditions/?lang=ar", LegalPages.url(LegalPage.TERMS, AppLanguage.ARABIC))
        assertEquals("https://distrigoo.com/suppression-compte/?lang=ar", LegalPages.url(LegalPage.ACCOUNT_DELETION, AppLanguage.ARABIC))
    }
}
