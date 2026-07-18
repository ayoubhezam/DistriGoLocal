package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * حقل بحث/اختيار العميل. عندما selectedClientName != null يظهر الاسم بدل placeholder
 * مع أيقونة "×" لإلغاء التحديد. البحث الفعلي (نتائج، فلترة) لم يُبنَ بعد.
 */
@Composable
fun ClientSearchBar(
    selectedClientName: String?,
    onSearchBarClick: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Rechercher un client..." // إضافة جديدة
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.SurfaceSunken, DsShapes.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSearchBarClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(17.dp))
        Text(
            text = selectedClientName ?: placeholder,
            fontSize = DsTextSize.body,
            fontWeight = FontWeight.Medium,
            color = if (selectedClientName != null) DsColors.TextPrimary else DsColors.TextTertiary,
            modifier = Modifier.weight(1f)
        )
        if (selectedClientName != null) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Effacer",
                tint = DsColors.TextTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClearSelection
                    )
            )
        }
    }
}

/** بطاقة ترويسة ملف العميل: الأفاتار + الاسم + الفئة + العنوان */
@Composable
fun ClientProfileHeaderCard(
    initials: String,
    name: String,
    category: ClientCategoryTag,
    address: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(DsColors.PrimaryLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(category.bgColor, DsShapes.pill)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(category.label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = category.textColor)
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(14.dp))
                Text(address, fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary, maxLines = 1)
            }
        }
    }
}

/** قائمة "المنتجات الأكثر شراءً" — بطاقة واحدة تحتوي كل الصفوف مع فواصل */
@Composable
fun TopProductsCard(products: List<TopProduct>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(horizontal = DsSpacing.lg)
    ) {
        products.forEachIndexed { index, product ->
            TopProductRow(product)
            if (index != products.lastIndex) {
                androidx.compose.material3.HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun TopProductRow(product: TopProduct) {
    Column(modifier = Modifier.padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(28.dp).background(DsColors.SurfaceSunken, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${product.rank}", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.TextSecondary)
            }
            Text(product.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(product.quantityLabel, fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
        }
        Box(
            modifier = Modifier
                .padding(start = 40.dp, top = 8.dp)
                .fillMaxWidth()
                .height(5.dp)
                .background(DsColors.Border, DsShapes.pill)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((product.percentOfMax / 100f).coerceIn(0f, 1f))
                    .background(DsColors.Primary, DsShapes.pill)
            )
        }
    }
}

/** رسم بياني شريطي لتطور المشتريات الشهرية + عبارة اتجاه أعلاه */
@Composable
fun MonthlyEvolutionChart(
    monthlyPurchases: List<MonthlyPurchase>,
    trendLabel: String,
    trendIsUp: Boolean,
    modifier: Modifier = Modifier
) {
    val maxAmount = monthlyPurchases.maxOfOrNull { it.amount }?.coerceAtLeast(1) ?: 1
    val trendColor = if (trendIsUp) DsColors.Success else DsColors.Warning

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (trendIsUp) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                contentDescription = null,
                tint = trendColor,
                modifier = Modifier.size(14.dp)
            )
            Text(trendLabel, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = trendColor)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            monthlyPurchases.forEach { month ->
                val heightFraction = month.amount.toFloat() / maxAmount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFraction.coerceIn(0.04f, 1f))
                        .background(DsColors.Primary, DsShapes.small)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            monthlyPurchases.forEach { month ->
                Text(
                    text = month.monthLabel,
                    modifier = Modifier.weight(1f),
                    fontSize = DsTextSize.caption,
                    color = DsColors.TextTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** زر "Imprimer le rapport client" بعرض كامل */
@Composable
fun PrintClientReportButton(onClick: () -> Unit,
                            modifier: Modifier = Modifier,
                            label: String = "Imprimer le rapport client" // إضافة جديدة
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(DsColors.Primary, DsShapes.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Print, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
        Text(
            label,
            fontSize = DsTextSize.body,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}