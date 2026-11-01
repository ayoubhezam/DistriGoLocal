package com.distrigo.app.data.license

import androidx.annotation.StringRes
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText

/** A license or account message in the app's language, written when it is told: a banner, a refusal, a notification. */
fun licText(@StringRes id: Int, vararg args: Any): String = AppStrings.write(UiText.Res(id, args.toList()))
