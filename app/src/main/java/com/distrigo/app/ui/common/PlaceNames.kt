package com.distrigo.app.ui.common

import com.distrigo.app.core.locale.AppLanguage
import com.distrigo.app.data.geo.GeoRepository
import com.distrigo.app.data.model.Commune
import com.distrigo.app.data.model.Wilaya
import java.util.Locale

/**
 * Wilaya and commune names in the app's language (D8): `nameAr` from assets/wilayas.json in Arabic, `nameFr` otherwise.
 * Display only: what is stored, searched by the import and printed stays `nameFr`. The language is the default
 * locale's, which MainActivity sets to the app's, so these also work in a list's label lambda.
 */
private fun arabic(): Boolean = AppLanguage.of(Locale.getDefault()) == AppLanguage.ARABIC

fun Wilaya.shownName(): String = if (arabic()) nameAr else nameFr

fun Commune.shownName(): String = if (arabic()) nameAr else nameFr

/** A stored (French) wilaya name, shown in the app's language; a name the list does not know is shown as stored. */
fun wilayaShownName(stored: String?): String? =
    if (stored == null || !arabic()) stored else GeoRepository.findWilayaByFrName(stored.trim())?.nameAr ?: stored

/** A stored commune name, looked up in its stored [wilaya]. */
fun communeShownName(wilaya: String?, stored: String?): String? {
    if (stored == null || !arabic()) return stored
    val code = GeoRepository.findWilayaByFrName(wilaya?.trim())?.wilayaCode ?: return stored
    return GeoRepository.findCommuneByFrName(code, stored.trim())?.nameAr ?: stored
}
