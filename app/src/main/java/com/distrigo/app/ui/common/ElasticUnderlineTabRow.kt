package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun ElasticUnderlineTabRow(
    tabs       : List<String>,
    pagerState : PagerState,
    modifier   : Modifier = Modifier
) {
    val density         = LocalDensity.current
    val coroutineScope  = rememberCoroutineScope()
    val tabOffsetsPx    = remember(tabs.size) { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(0f) } } }
    val tabWidthsPx     = remember(tabs.size) { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(0f) } } }

    Box(modifier = modifier.fillMaxWidth().background(DsColors.Surface)) {
        val pageFraction = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, (tabs.size - 1).toFloat())

        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                val proximity = (1f - abs(pageFraction - index)).coerceIn(0f, 1f)
                val tint = lerp(DsColors.TextSecondary, DsColors.Primary, proximity)
                Text(
                    text       = title,
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = if (proximity > 0.5f) FontWeight.Bold else FontWeight.Medium,
                    color      = tint,
                    modifier   = Modifier
                        .onGloballyPositioned {
                            tabOffsetsPx[index] = it.positionInParent().x
                            tabWidthsPx[index]  = it.size.width.toFloat()
                        }
                        .clickable { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                )
            }
        }

        if (tabWidthsPx.all { it > 0f }) {
            val leftIndex  = floor(pageFraction).toInt().coerceIn(0, tabs.size - 1)
            val rightIndex = ceil(pageFraction).toInt().coerceIn(0, tabs.size - 1)
            val localT     = pageFraction - leftIndex

            val indicatorLeftPx: Float
            val indicatorRightPx: Float
            if (leftIndex == rightIndex) {
                indicatorLeftPx  = tabOffsetsPx[leftIndex]
                indicatorRightPx = tabOffsetsPx[leftIndex] + tabWidthsPx[leftIndex]
            } else {
                val easeIn  = localT * localT
                val easeOut = 1f - (1f - localT) * (1f - localT)
                indicatorLeftPx  = lerpFloat(tabOffsetsPx[leftIndex], tabOffsetsPx[rightIndex], easeIn)
                indicatorRightPx = lerpFloat(
                    tabOffsetsPx[leftIndex] + tabWidthsPx[leftIndex],
                    tabOffsetsPx[rightIndex] + tabWidthsPx[rightIndex],
                    easeOut
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(indicatorLeftPx.roundToInt(), 0) }
                    .width(with(density) { (indicatorRightPx - indicatorLeftPx).toDp() })
                    .height(2.5.dp)
                    .clip(DsShapes.pill)
                    .background(DsColors.Primary)
            )
        }

        HorizontalDivider(
            modifier  = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            color     = DsColors.Border,
            thickness = 1.dp
        )
    }
}
