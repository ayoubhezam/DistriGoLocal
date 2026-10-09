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
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
/**
 * Bottom sheet عام لاختيار عنصر واحد من قائمة مع بحث نصي.
 * قابل لإعادة الاستخدام: الولاية، البلدية، ولاحقًا القطاع (Secteur).
 *
 * A [SearchableSelectList] in a sheet of its own: for a form's field (wilaya, commune). A filter sheet
 * shows the list in its own place instead — see [SearchableSelectList].
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SearchableSelectList(
            title       = title,
            items       = items,
            itemLabel   = itemLabel,
            onSelect    = { onSelect(it); onDismiss() },
            itemKey     = itemKey,
            isSelected  = isSelected,
            allLabel    = allLabel,
            allSelected = allSelected,
            onSelectAll = { onSelectAll(); onDismiss() },
            modifier    = Modifier.padding(horizontal = DsSpacing.md).padding(bottom = DsSpacing.lg),
        )
    }
}

/**
 * Choosing one of many: a title, a search box and the items in a lazy list — only the rows on screen
 * are built, and the search narrows the rest. Every word typed, in any order (see [matchesAllTokens]).
 *
 * The filters' choices — Client in the Ventes and tournée filters, Fournisseur in Achats', the party in
 * Mouvements' — show this list in place of the filter sheet's own content, with [onBack] taking them
 * back to it. A sheet of its own over the filter sheet would be a second window: tracing that opening
 * found 64–82 ms first frames, ~45 ms of them the window being created (UI fluidity audit, "Screen
 * openings: traced"). With [onBack], the title carries a back arrow; the system Back is the host's
 * to route — a filter sheet's is, see [FilterSheet].
 *
 * [itemKey] keeps each row's place as the search narrows the list; [isSelected] marks the current
 * choice. [allLabel] adds a filter's "Tous les …" row above the items while nothing is searched, marked
 * when [allSelected]; tapping it calls [onSelectAll].
 */
@Composable
fun <T> SearchableSelectList(
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemKey: ((T) -> Any)? = null,
    isSelected: (T) -> Boolean = { false },
    allLabel: String? = null,
    allSelected: Boolean = false,
    onSelectAll: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, items) {
        val tokens = searchTokens(query)
        items.filter { matchesAllTokens(tokens, itemLabel(it)) }
    }

    Column(modifier = Modifier.fillMaxWidth().imePadding().then(modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                }
            }
            Text(
                title,
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
        }
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
                    SelectRow(allLabel, selected = allSelected, muted = true) { onSelectAll() }
                }
            }
            items(filtered, key = itemKey) { item ->
                SelectRow(itemLabel(item), selected = isSelected(item)) { onSelect(item) }
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
