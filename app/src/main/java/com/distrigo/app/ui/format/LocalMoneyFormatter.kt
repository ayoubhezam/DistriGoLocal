package com.distrigo.app.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter

/**
 * How this composition writes amounts: `LocalMoneyFormatter.current.da(total)`.
 *
 * Provided once, at the root, from the business's "Format des montants" — see [ProvideMoneyFormat].
 * Static rather than dynamic: the format changes a few times in an app's life, so nothing should pay to
 * track each read; when it does change the whole tree recomposes once, which is exactly what a
 * format change means — every amount on screen is rewritten.
 *
 * Outside a provider — a preview, a test — it is the default format, so a screen never crashes for
 * want of one. Read it in composition, not inside a `Canvas` or `drawBehind` lambda: those are not
 * composition, and take the formatter read just before them.
 */
val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter.of(MoneyFormat.DEFAULT) }

/** Makes [format] the way every amount in [content] is written. */
@Composable
fun ProvideMoneyFormat(format: MoneyFormat, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMoneyFormatter provides MoneyFormatter.of(format), content = content)
}
