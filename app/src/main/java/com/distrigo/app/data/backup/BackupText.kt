package com.distrigo.app.data.backup

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText

/** A backup message in the app's language, written when it is told: a screen's, a notification's, a worker's step. */
internal fun backupText(@StringRes id: Int, vararg args: Any): String = AppStrings.write(UiText.Res(id, args.toList()))

internal fun backupCount(@PluralsRes id: Int, count: Int): String = AppStrings.write(UiText.Plural(id, count))
