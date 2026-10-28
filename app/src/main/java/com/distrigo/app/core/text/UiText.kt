package com.distrigo.app.core.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text to show, before it is written in a language (docs/i18n_rtl_plan.md §1): what a ViewModel holds in its state and
 * a repository says when it refuses, without a Context. The screen writes it ([com.distrigo.app.ui.common.asString])
 * in the app's language at that moment, so text a ViewModel kept across a language change still follows it.
 *
 * [args] are the values put into the string's `%1$s`, `%2$d`…, already formatted (an amount by LocalMoneyFormatter, a
 * date by AppDates); a [UiText] among them is written first. Equal when the resource and the arguments are, so a
 * test asserts what is shown without Android resources.
 */
sealed interface UiText {

    /** Text that is not the app's own wording: a name, a server's or a file's words, what was typed. */
    data class Raw(val text: String) : UiText

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    /** A count's sentence, in the form the language takes for [count] (Arabic has six). */
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** "SARL Benali · 5 produits · il y a 3 h": [parts] written one after the other, [separator] between them. */
    data class Joined(val parts: List<UiText>, val separator: String = " · ") : UiText

    /** Written in [context]'s language: for what is not a screen (a notification, a Toast). */
    fun resolve(context: Context): String = when (this) {
        is Raw -> text
        is Joined -> parts.joinToString(separator) { it.resolve(context) }
        is Res -> context.getString(id, *args.map { (it as? UiText)?.resolve(context) ?: it }.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.map { (it as? UiText)?.resolve(context) ?: it }.toTypedArray())
    }
}

/**
 * A refusal the user is told about, in the app's words: what a repository throws instead of a French sentence. The
 * screens read it through userText (ui/common/ErrorMessage.kt).
 */
class UserFacingException(val text: UiText, cause: Throwable? = null) : RuntimeException(text.toString(), cause)
