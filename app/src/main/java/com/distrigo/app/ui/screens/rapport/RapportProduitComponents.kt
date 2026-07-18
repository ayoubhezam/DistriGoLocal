package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/** بطاقة ترويسة ملف المنتج: أيقونة مربعة + الاسم + الفئة */
@Composable
fun ProductProfileHeaderCard(
    productName: String,
    categoryLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(DsColors.PrimaryLight, DsShapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(productName, fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(categoryLabel, fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** قائمة "أفضل العملاء لهذا المنتج" — نفس نمط TopProductsCard لكن بترتيب رتب/كأس بدل شريط تقدّم */
@Composable
fun RankedClientsCard(clients: List<RankedClient>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(horizontal = DsSpacing.lg)
    ) {
        clients.forEachIndexed { index, client ->
            RankedClientRow(client)
            if (index != clients.lastIndex) {
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun RankedClientRow(client: RankedClient) {
    Row(
        modifier = Modifier.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (client.isTopRank) {
            Box(
                modifier = Modifier.size(28.dp).background(DsColors.TagWholesale.second, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = DsColors.TagWholesale.first, modifier = Modifier.size(15.dp))
            }
        } else {
            Box(
                modifier = Modifier.size(28.dp).background(DsColors.SurfaceSunken, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${client.rank}", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.TextSecondary)
            }
        }
        Text(client.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(client.quantityLabel, fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
    }
}