package com.distrigo.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * زر دائري عائم يتحرك بين وضعين: أعلى الشريط السفلي (عند الظهور)، أو ملاصقًا لأسفل الشاشة (عند الطي).
 * bottomPadding القيمة المُتحرِّكة يجب تطبيقها من مكان الاستخدام عبر Modifier.padding.
 */
@Composable
fun rememberFabBottomPadding(
    collapsed             : Boolean,
    restingBottomPadding  : Dp = 15.dp,
    collapsedBottomPadding: Dp = 16.dp
): State<Dp> {
    return animateDpAsState(
        targetValue   = if (collapsed) collapsedBottomPadding else restingBottomPadding,
        animationSpec = tween(280),
        label         = "fab_bottom"
    )
}

@Composable
fun ScrollAwareFab(
    onClick        : () -> Unit,
    modifier       : Modifier = Modifier,
    containerColor : Color = Color(0xFF2196F3)
) {
    FloatingActionButton(
        onClick        = onClick,
        modifier       = modifier,
        containerColor = containerColor,
        shape          = CircleShape
    ) {
        Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = Color.White)
    }
}
