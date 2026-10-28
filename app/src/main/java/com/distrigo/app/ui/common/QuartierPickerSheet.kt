package com.distrigo.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.lists.QuartierSummary
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.distrigo.app.R

/** Past this many quartiers, the sheet gets a search (owner's prototype v5). */
private const val SEARCH_FROM = 6

/**
 * The secteur's quartiers for a client (owner's prototype v5, 2026-10-24): « Aucun quartier » first to clear it, then
 * each quartier with its count of clients. Picking a row reports it (null for none) and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuartierPickerSheet(
    quartiers  : List<QuartierSummary>,
    selectedId : Long?,
    onDismiss  : () -> Unit,
    onSelect   : (QuartierSummary?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val tokens = searchTokens(query)
    val shown = quartiers.filter { matchesAllTokens(tokens, it.name) }
    val pick: (QuartierSummary?) -> Unit = { onSelect(it); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = DsSpacing.md)
                .padding(bottom = DsSpacing.lg)
        ) {
            Text(stringResource(R.string.common_label_quartier), fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
            Spacer(Modifier.height(DsSpacing.sm))
            if (quartiers.size > SEARCH_FROM) {
                DsCompactSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.common_search_quartier))
                Spacer(Modifier.height(DsSpacing.sm))
            }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "none") {
                    QuartierRow(stringResource(R.string.common_no_quartier), sub = null, selected = selectedId == null, muted = true) { pick(null) }
                }
                items(shown, key = { it.id }) { quartier ->
                    QuartierRow(
                        quartier.name,
                        sub = pluralStringResource(R.plurals.common_clients_count, quartier.clients, quartier.clients),
                        selected = quartier.id == selectedId,
                    ) { pick(quartier) }
                }
                when {
                    quartiers.isEmpty() -> item(key = "empty") { Note(stringResource(R.string.common_secteur_no_quartier)) }
                    shown.isEmpty() -> item(key = "no-match") { Note(stringResource(R.string.common_no_quartier_matches, query)) }
                }
            }
        }
    }
}

@Composable
private fun QuartierRow(name: String, sub: String?, selected: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize   = DsTextSize.body,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (muted && !selected) DsColors.TextSecondary else DsColors.TextPrimary
            )
            sub?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.common_cd_selected), tint = DsColors.Primary, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}

@Composable
private fun Note(text: String) {
    Text(text, color = DsColors.TextSecondary, modifier = Modifier.padding(vertical = DsSpacing.md))
}
