package com.distrigo.app.core.text

import android.content.Context
import androidx.annotation.StringRes
import com.distrigo.app.core.locale.AppLanguage

/**
 * [UiText] written where no screen is at hand: what a [UserFacingException] says when its `message` is read (by
 * `userMessage`, a ViewModel, a log). DistriGoApplication installs the application's context at startup, so device
 * tests, which run the real Application, read French as the screens do. In a JVM unit test nothing is installed and
 * the text reads as its `toString()`: such a test compares the exception's [UserFacingException.text] instead.
 */
object AppStrings {

    @Volatile
    private var app: Context? = null

    fun install(context: Context) {
        app = context.applicationContext
    }

    /** [text] in the app's language now (AppLanguage.localized: below Android 13 the Application follows the phone). */
    fun write(text: UiText): String = app?.let { text.resolve(AppLanguage.localized(it)) } ?: text.toString()
}

/** Refuses an operation in the app's words: the repositories' `throw IllegalStateException("…")`, translatable. */
fun refuse(@StringRes id: Int, vararg args: Any): Nothing = throw UserFacingException(UiText.Res(id, *args))
