package com.distrigo.app.core.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language, chosen on this phone (docs/i18n_rtl_plan.md §4). French until the user picks Arabic (D1, approved
 * 2026-10-27): a phone in Arabic does not switch DistriGo by itself. A preference of this phone, not of the business:
 * not in business_settings, not in a backup, so a restore never changes it.
 *
 * From Android 13 the system keeps the choice (per-app language, also under Settings › Apps › DistriGo › Language) and
 * restarts the screens itself. Before 13 it is kept here, in a small preferences file read when an activity is created
 * ([localesFor]), and the activity is recreated on a change.
 *
 * Each language is named in itself — « Français », « العربية » — so it reads whatever language the app is in.
 */
enum class AppLanguage(val tag: String, val endonym: String) {
    FRENCH("fr", "Français"),
    ARABIC("ar-DZ", "العربية");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        val DEFAULT = FRENCH

        /** The language a stored or system locale names; anything else is the default. */
        fun of(locale: Locale?): AppLanguage = entries.firstOrNull { it.locale.language == locale?.language } ?: DEFAULT

        /** The language in effect on this phone. */
        fun current(context: Context): AppLanguage =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val chosen = context.getSystemService(LocaleManager::class.java).applicationLocales
                if (chosen.isEmpty) DEFAULT else of(chosen[0])
            } else {
                of(stored(context)?.let(Locale::forLanguageTag))
            }

        /**
         * Pins French on a phone where nothing was chosen yet (D1), once: from Android 13 the system would otherwise
         * show DistriGo in the phone's language. Before 13 there is nothing to do, French is what [localesFor] gives.
         * Called at launch, before any activity.
         */
        fun pinDefault(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || prefs(context).contains(KEY_TAG)) return
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(DEFAULT.tag)
            prefs(context).edit().putString(KEY_TAG, of(manager.applicationLocales[0]).tag).apply()
        }

        /**
         * Makes [language] the app's: from Android 13 through the system, which recreates the screens; before, stored
         * here and [activity] recreated so it is built again in the new language.
         */
        fun set(activity: Activity, language: AppLanguage) {
            prefs(activity).edit().putString(KEY_TAG, language.tag).apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
            } else {
                activity.recreate()
            }
        }

        /**
         * The locales an activity is built with, over [base]: before Android 13 the chosen language (French when none
         * was), from 13 those the system already gives. MainActivity applies them, with Latin digits.
         */
        fun localesFor(context: Context, base: Configuration): LocaleList =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) base.locales
            else LocaleList(of(stored(context)?.let(Locale::forLanguageTag)).locale)

        /**
         * [context] in the app's language, for what is written outside an activity before Android 13 (a notification,
         * a Toast from the Application): the application's own resources there follow the phone, not the app.
         */
        fun localized(context: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
            val config = Configuration(context.resources.configuration).apply { setLocale(current(context).locale) }
            return context.createConfigurationContext(config)
        }

        private fun stored(context: Context): String? = prefs(context).getString(KEY_TAG, null)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private const val PREFS = "app_language"
        private const val KEY_TAG = "tag"
    }
}
