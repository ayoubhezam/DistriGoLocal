package com.distrigo.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * A list with nothing in it yet: its illustration (`res/drawable/empty_*`), what is missing, and what to do about it
 * (owner's design, 2026-11-04). The illustration is a drawing, not a direction: it is not mirrored in Arabic.
 * Centre it in the list's space; the caller decides when the list is empty rather than filtered to nothing.
 */
@Composable
fun EmptyState(
    @DrawableRes illustration: Int,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = DsSpacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(illustration), contentDescription = null, modifier = Modifier.size(ILLUSTRATION_SIZE))
        Spacer(Modifier.height(DsSpacing.sm))
        Text(title, color = DsColors.TextPrimary, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        hint?.let {
            Spacer(Modifier.height(DsSpacing.xs))
            Text(it, color = DsColors.TextSecondary, fontSize = DsTextSize.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

private val ILLUSTRATION_SIZE = 160.dp
