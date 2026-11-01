package com.distrigo.app.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * A phone number in a field, typed or as its example: read left to right in every language, so « 0555 12 34 56 »
 * keeps its groups in order under Arabic (whose direction would otherwise lay the groups out from the right), and
 * set on the side the layout starts, as the field's other text.
 */
@Composable
fun phoneTextStyle(base: TextStyle = LocalTextStyle.current): TextStyle = base.copy(
    textDirection = TextDirection.Ltr,
    textAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
)
