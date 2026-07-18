// package com.distrigo.app.ui.screens.rapport

package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * صف اختيار الفترة الزمنية (Aujourd'hui / Semaine / Mois / Personnalisé)
 * مطابق لتصميم "pill segmented control" في الملف المرفوع.
 *
 * Stateless بالكامل — الحالة تبقى في الشاشة الأم (state hoisting).
 */
@Composable
fun PeriodSelectorChips(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.SurfaceSunken, DsShapes.pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ReportPeriod.entries.forEach { period ->
            val isSelected = period == selectedPeriod
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) DsColors.Primary else Color.Transparent,
                        shape = DsShapes.pill
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPeriodSelected(period) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.label,
                    fontSize = DsTextSize.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else DsColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * بطاقة مؤشر (KPI) عامة تُستخدم في كل التبويبات الأربعة.
 * تُبقي المنطق البصري فقط — لا حسابات، لا وصول لبيانات.
 */
@Composable
fun StatCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    trend: TrendInfo? = null
) {
    Box(
        modifier = modifier
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                fontSize = DsTextSize.headline,
                fontWeight = FontWeight.Bold,
                color = DsColors.TextPrimary
            )
            Text(
                text = label,
                fontSize = DsTextSize.bodySmall,
                fontWeight = FontWeight.Medium,
                color = DsColors.TextSecondary
            )
        }

        if (trend != null) {
            TrendPill(
                trend = trend,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/** شارة صغيرة (▲12% / ▼8%) تُقارن بالفترة السابقة */
@Composable
fun TrendPill(trend: TrendInfo, modifier: Modifier = Modifier) {
    val bg = if (trend.isPositive) DsColors.SuccessLight else DsColors.DangerLight
    val fg = if (trend.isPositive) DsColors.Success else DsColors.Danger
    val arrow = if (trend.isPositive) "▲" else "▼"

    Row(
        modifier = modifier
            .background(bg, DsShapes.pill)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = arrow, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = fg)
        Text(text = "${trend.percentage}%", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = fg)
    }
}