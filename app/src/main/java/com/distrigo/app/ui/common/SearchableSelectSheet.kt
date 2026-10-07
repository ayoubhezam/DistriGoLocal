package com.distrigo.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
/**
 * Bottom sheet عام لاختيار عنصر واحد من قائمة مع بحث نصي.
 * قابل لإعادة الاستخدام: الولاية، البلدية، ولاحقًا القطاع (Secteur).
 *
 * Also a filter's "one of them, or all of them" choice — Client in the Ventes and tournée filters,
 * Fournisseur in Achats', the party in Mouvements'. Those used to be dropdown menus, which build every
 * row the moment they open: a 300 ms freeze for ~1,500 clients in a release build (the UI fluidity
 * audit's C2). Here only the rows on screen are built, and the search narrows the rest.
 *
 * [itemKey] keeps each row's place as the search narrows the list; [isSelected] marks the current
 * choice. [allLabel] adds the filter's "Tous les …" row above the items while nothing is searched,
 * marked when [allSelected]; tapping it calls [onSelectAll].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableSelectSheet(
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    itemKey: ((T) -> Any)? = null,
    isSelected: (T) -> Boolean = { false },
    allLabel: String? = null,
    allSelected: Boolean = false,
    onSelectAll: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, items) {
        val tokens = searchTokens(query)
        items.filter { matchesAllTokens(tokens, itemLabel(it)) }
    }


    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
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

            DsCompactSearchField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = "Rechercher",
                modifier      = Modifier
            )
            Spacer(Modifier.height(DsSpacing.sm))

            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (allLabel != null && query.isBlank()) {
                    item(key = ALL_ROW_KEY) {
                        SelectRow(allLabel, selected = allSelected, muted = true) { onSelectAll(); onDismiss() }
                    }
                }
                items(filtered, key = itemKey) { item ->
                    SelectRow(itemLabel(item), selected = isSelected(item)) { onSelect(item); onDismiss() }
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

/** One choice: its label, and a check when it is the current one. */
@Composable
private fun SelectRow(label: String, selected: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text       = label,
            fontSize   = DsTextSize.body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = when {
                selected -> DsColors.Primary
                muted    -> DsColors.TextSecondary
                else     -> DsColors.TextPrimary
            },
            modifier   = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Sélectionné", tint = DsColors.Primary, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}

/** The "Tous les …" row's key: a string, so it can never equal an item's numeric id. */
private const val ALL_ROW_KEY = "searchable_select_all"
