package com.distrigo.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity

/**
 * يحدّد ما إذا كان يجب "طي" العناصر العلوية/السفلية بناءً على اتجاه التمرير.
 * true = يُطوى (المستخدم يمرر للأسفل) — false = يظهر بالكامل (أعلى القائمة أو يمرر للأعلى)
 */
@Composable
fun rememberScrollCollapsed(listState: LazyListState): State<Boolean> {
    val collapsed = remember { mutableStateOf(false) }
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.canScrollForward
            )
        }.collect { (index, offset, canScrollForward) ->
            if (!canScrollForward && !listState.canScrollBackward) {
                // كل المحتوى ظاهر بالفعل ضمن الشاشة، لا داعي لأي طي إطلاقًا
                collapsed.value = false
            } else {
                when {
                    index == 0 && offset < 8 -> collapsed.value = false
                    index > lastIndex || (index == lastIndex && offset > lastOffset + 8) -> collapsed.value = true
                    index < lastIndex || offset < lastOffset - 8 -> collapsed.value = false
                }
            }
            lastIndex = index
            lastOffset = offset
        }
    }
    return collapsed
}

/**
 * غلاف عام لأي محتوى علوي (بطاقة إحصائيات، شريط بحث...) يظهر/يختفي مع التمرير.
 * height: الارتفاع الكامل للمحتوى عند الظهور (لحساب مسافة الانزلاق).
 */
@Composable
fun CollapsibleHeader(
    collapsed : Boolean,
    modifier  : Modifier = Modifier,
    content   : @Composable () -> Unit
) {
    val density = LocalDensity.current
    var measuredHeight by remember { mutableStateOf(0.dp) }

    val animatedHeight by animateDpAsState(
        targetValue   = if (collapsed) 0.dp else measuredHeight,
        animationSpec = tween(280),
        label         = "header_height"
    )
    val alpha by animateFloatAsState(
        targetValue   = if (collapsed) 0f else 1f,
        animationSpec = tween(180),
        label         = "header_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { if (measuredHeight > 0.dp) it.height(animatedHeight) else it }
            .clip(RectangleShape)
            .alpha(alpha)
    ) {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                if (measuredHeight == 0.dp) {
                    measuredHeight = with(density) { coordinates.size.height.toDp() }
                }
            }
        ) {
            content()
        }
    }
}
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