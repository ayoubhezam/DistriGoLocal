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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.common.EntityImage

private val avatarColors = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
    Color(0xFFC62828), Color(0xFFE65100), Color(0xFF00695C)
)

@Composable
fun EntityAvatar(
    name     : String,
    imageUri : String?,
    size     : Dp = 42.dp,
    modifier : Modifier = Modifier
) {
    val color    = avatarColors[name.firstOrNull()?.code?.rem(avatarColors.size) ?: 0]
    val initials = name.split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Box(
        modifier         = modifier
            .size(size)
            .clip(DsShapes.medium)
            .background(color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        EntityImage(
            ref                = imageUri,
            contentDescription = name,
            modifier           = Modifier.fillMaxSize().clip(DsShapes.medium)
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