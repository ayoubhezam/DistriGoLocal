package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText
import com.distrigo.app.core.text.UserFacingException
import com.distrigo.app.data.local.database.LicenseWriteGate
import com.distrigo.app.ui.designsystem.LicenseRefusals

/**
 * The message a screen shows when an operation fails: the exception's own message, which the
 * repositories write for the user ("Stock insuffisant pour …"), or a generic one.
 *
 * This used to live beside the Retrofit client and also unpacked the `error` field of an HTTP error
 * body. The app stopped talking to that server when it moved to Room, and no exception here is an
 * HTTP one any more, so that branch never ran; what remains is exactly what every call has returned.
 *
 * A write the license's gate refused (docs/license_l5_enforcement.md §3) gets the gate's French sentence rather
 * than SQLite's.
 */
fun extractErrorMessage(e: Exception): String = userMessage(e)

/**
 * [extractErrorMessage] for any failure, with the screen's own [fallback]. A refusal by the license's gate also opens
 * the « Consultation seule » sheet (LicenseRefusals), so a write no screen guarded still says why.
 */
fun userMessage(e: Throwable, fallback: String = AppStrings.write(UiText.Res(R.string.common_error_unknown))): String =
    if (LicenseWriteGate.isRefusal(e)) {
        LicenseRefusals.report()
        LicenseWriteGate.MESSAGE
    } else {
        e.message ?: fallback
    }

/**
 * Runs a repository write that reports refusals as an `"error"` entry, and makes anything it throws one too, in
 * [userMessage]'s words: a refusal by the license's gate, a database error. The forms that call it show the entry and
 * stay open, rather than the app stopping. A cancellation still propagates.
 */
suspend fun caughtAsError(block: suspend () -> Map<String, Any>): Map<String, Any> =
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        mapOf("error" to userMessage(e))
    }

/**
 * [userMessage] as [UiText], for a screen that writes it in the app's language (UiText.asString): a refusal a
 * repository threw as a UserFacingException in its own words, the license gate's sentence, or [fallback]. Until a
 * repository's French sentences have moved to resources (Phase 3), its message passes through as it is.
 */
fun userText(e: Throwable, fallback: UiText = UiText.Res(R.string.common_error_unknown)): UiText = when {
    e is UserFacingException -> e.text
    LicenseWriteGate.isRefusal(e) -> {
        LicenseRefusals.report()
        UiText.Raw(LicenseWriteGate.MESSAGE)
    }
    else -> e.message?.let { UiText.Raw(it) } ?: fallback
}
