package com.distrigo.app.ui.common

import com.distrigo.app.data.local.database.LicenseWriteGate

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
fun extractErrorMessage(e: Exception): String =
    if (LicenseWriteGate.isRefusal(e)) LicenseWriteGate.MESSAGE else e.message ?: "Erreur inconnue"
