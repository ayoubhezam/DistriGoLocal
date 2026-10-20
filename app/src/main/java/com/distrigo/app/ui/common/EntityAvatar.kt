package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.designsystem.DsColors

// The brand's blue and navy and the accents: white initials stay legible on each
private val avatarColors = listOf(
    DsColors.Primary, DsColors.AccentTeal, DsColors.AccentViolet,
    DsColors.AccentRose, DsColors.AccentOrange, DsColors.PrimaryDark
)

/** The palette colour of [name]'s initials: the same name always gets the same one. */
fun avatarColor(name: String): Color = avatarColors[name.firstOrNull()?.code?.rem(avatarColors.size) ?: 0]

@Composable
fun EntityAvatar(
    name     : String,
    imageUri : String?,
    size     : Dp = 42.dp,
    modifier : Modifier = Modifier,
    /** The app's rounded square; the Dashboard's « Clients du jour » asks for a circle. */
    shape    : Shape = DsShapes.medium,
) {
    val color    = avatarColor(name)
    val initials = name.split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Box(
        modifier         = modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        EntityImage(
            ref                = imageUri,
            contentDescription = name,
            modifier           = Modifier.fillMaxSize().clip(shape)
        ) {
            Text(
                text       = initials,
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.Bold,
                color      = color
            )
        }
    }
}