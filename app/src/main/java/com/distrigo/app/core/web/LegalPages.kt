package com.distrigo.app.core.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.distrigo.app.core.locale.AppLanguage

/** The legal pages on distrigoo.com (published 2026-11-18): French by default, Arabic with `?lang=ar`. */
enum class LegalPage(internal val path: String) {
    PRIVACY("confidentialite"),
    TERMS("conditions"),
    ACCOUNT_DELETION("suppression-compte"),
}

object LegalPages {
    private const val SITE = "https://distrigoo.com"

    /** The page in [language]: the trailing slash is the address the site serves without a redirect. */
    fun url(page: LegalPage, language: AppLanguage): String =
        "$SITE/${page.path}/" + if (language == AppLanguage.ARABIC) "?lang=ar" else ""

    /** Opens [page] in the browser, in the app's language; false when no app can open a web page. */
    fun open(context: Context, page: LegalPage): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url(page, AppLanguage.current(context))))
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
