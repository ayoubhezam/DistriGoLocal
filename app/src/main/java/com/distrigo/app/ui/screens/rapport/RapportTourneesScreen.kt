// package com.distrigo.app.ui.screens.rapport

package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import com.distrigo.app.ui.designsystem.DsSpacing
/**
 * نقطة الدخول لشاشة "Rapport des tournées".
 * تحتوي فقط على الهيكل (TabRow + Pager) — كل تبويب مسؤول عن حالته وبياناته بنفسه.
 *
 * ملاحظة أداء: HorizontalPager لا يُركّب سوى الصفحة الحالية (والمجاورة لها عند التمرير)،
 * لذا كل تبويب يجلب بياناته فقط عند أول ظهور له فعليًا — لا حاجة لآلية lazy-loading يدوية إضافية هنا.
 */
@Composable
fun RapportTourneesScreen(
    modifier: Modifier = Modifier
) {
    val tabs = remember { RapportTab.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.SurfaceMuted)
    ) {
        RapportTabRow(
            tabs = tabs,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (tabs[page]) {
                RapportTab.TABLEAU_DE_BORD -> TableauDeBordTab()
                RapportTab.VENTES -> RapportVentesTab()
                RapportTab.CLIENTS -> RapportClientTab()
                RapportTab.PRODUITS -> RapportProduitTab()
            }
        }
    }
}
@Composable
fun RapportTourneesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { RapportTab.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.SurfaceMuted)
    ) {
        RapportTopBar(onBack = onBack)

        RapportTabRow(
            tabs = tabs,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (tabs[page]) {
                RapportTab.TABLEAU_DE_BORD -> TableauDeBordTab()
                RapportTab.VENTES -> RapportVentesTab()
                RapportTab.CLIENTS -> RapportClientTab()
                RapportTab.PRODUITS -> RapportProduitTab()
            }
        }
    }
}

@Composable
private fun RapportTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface)
            .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = DsColors.TextPrimary
            )
        }
        Text(
            text = "Rapport des tournées",
            fontSize = DsTextSize.title,
            fontWeight = FontWeight.SemiBold,
            color = DsColors.TextPrimary,
            modifier = Modifier.padding(start = DsSpacing.xs)
        )
    }
}

@Composable
private fun RapportTabRow(
    tabs: List<RapportTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(DsColors.Surface)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val contentColor = if (isSelected) DsColors.Primary else DsColors.TextTertiary

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) }
                        .padding(vertical = 8.dp, horizontal = 2.dp)
                        .drawBehind {
                            if (isSelected) {
                                drawLine(
                                    color = DsColors.Primary,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = tab.label,
                        fontSize = DsTextSize.caption,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
    }
}



