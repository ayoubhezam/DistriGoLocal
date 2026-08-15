package com.distrigo.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsTextSize

private val avatarColors = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
    Color(0xFFC62828), Color(0xFFE65100), Color(0xFF00695C)
)

private fun decodeBase64Bitmap(imageUri: String): Bitmap? = try {
    val bytes = Base64.decode(imageUri.substringAfter("base64,"), Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: Exception) {
    null
}

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

    val bitmap = remember(imageUri) {
        imageUri?.let { decodeBase64Bitmap(it) }
    }

    Box(
        modifier         = modifier
            .size(size)
            .clip(DsShapes.medium)
            .background(color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = name,
                modifier           = Modifier.fillMaxSize().clip(DsShapes.medium),
                contentScale       = ContentScale.Crop
            )
        } else {
            Text(
                text       = initials,
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.Bold,
                color      = color
            )
        }
    }
}