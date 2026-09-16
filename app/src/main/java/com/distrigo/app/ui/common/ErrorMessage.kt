package com.distrigo.app.ui.common

/**
 * The message a screen shows when an operation fails: the exception's own message, which the
 * repositories write for the user ("Stock insuffisant pour …"), or a generic one.
 *
 * This used to live beside the Retrofit client and also unpacked the `error` field of an HTTP error
 * body. The app stopped talking to that server when it moved to Room, and no exception here is an
 * HTTP one any more, so that branch never ran; what remains is exactly what every call has returned.
 */
fun extractErrorMessage(e: Exception): String = e.message ?: "Erreur inconnue"
