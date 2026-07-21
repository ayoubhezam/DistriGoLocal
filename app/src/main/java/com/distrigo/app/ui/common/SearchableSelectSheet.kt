package com.distrigo.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * Bottom sheet عام لاختيار عنصر واحد من قائمة مع بحث نصي.
 * قابل لإعادة الاستخدام: الولاية، البلدية، ولاحقًا القطاع (Secteur).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableSelectSheet(
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, items) {
        if (query.isBlank()) items
        else items.filter { itemLabel(it).contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.md)
                .padding(bottom = DsSpacing.lg)
        ) {
            Text(
                title,
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
            Spacer(Modifier.height(DsSpacing.sm))

            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Rechercher...") },
                singleLine    = true,
                shape         = DsShapes.medium,
                modifier      = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(DsSpacing.sm))

            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(filtered) { item ->
                    Text(
                        text     = itemLabel(item),
                        fontSize = DsTextSize.body,
                        color    = DsColors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item); onDismiss() }
                            .padding(vertical = DsSpacing.sm)
                    )
                    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Aucun résultat",
                            color    = DsColors.TextSecondary,
                            modifier = Modifier.padding(DsSpacing.md)
                        )
                    }
                }
            }
        }
    }
}