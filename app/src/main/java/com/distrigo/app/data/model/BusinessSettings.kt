package com.distrigo.app.data.model

/**
 * The business as its receipts show it.
 *
 * [name] is what prints — "DISTRIGO" until one is set. [logoPath] is the logo's file when it is on this
 * device: a database restored without its photos has a [logoRef] and no file, and prints no logo.
 */
data class BusinessSettings(
    val name: String,
    val phone: String?,
    val logoRef: String?,
    val logoPath: String?,
) {
    companion object {
        const val DEFAULT_NAME = "DISTRIGO"
    }
}
