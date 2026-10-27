package com.distrigo.app.core.format

import java.util.Locale

/**
 * A percentage after its figure, written as the app's language writes it (the owner's rule, 2026-10-27): `75%` in
 * Arabic, `75 %` in French. [number] is already written (0–9, the decimal mark chosen by the caller).
 *
 * The language is the default locale's, which Android sets to the app's language (and per-app language) at launch and
 * again when it changes, so a screen recomposed after a change writes the new way.
 */
fun percentText(number: String, locale: Locale = Locale.getDefault()): String =
    if (locale.language == "ar") "$number%" else "$number %"
