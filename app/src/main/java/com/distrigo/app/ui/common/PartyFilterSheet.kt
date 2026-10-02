package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.FilterDropdown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect

// The Clients and Fournisseurs lists' filtering: a count with a "Filtres" button, a removable chip
// per criterion applied, and the sheet. The two lists differ only in which sections they show.

/** The count on the left, "Filtres" on the right — blue with a dot while it narrows anything. */
@Composable
internal fun CountAndFiltersRow(
    label         : String,
    filtersActive : Boolean,
    onOpenFilters : () -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
        Step2Chip(
            icon      = Icons.Default.FilterList,
            label     = "Filtres",
            container = DsColors.SurfaceSunken,
            content   = if (filtersActive) DsColors.Primary else DsColors.TextSecondary,
            dot       = filtersActive,
            onClick   = onOpenFilters
        )
    }
}

/** What is narrowing the list, each removable on its own, then "Tout effacer". */
@Composable
internal fun RemovableFilterChips(
    chips      : List<Pair<String, () -> Unit>>,
    onClearAll : () -> Unit,
) {
    if (chips.isEmpty()) return
    // A chip added before the first one shown would otherwise leave the row anchored on the old
    // first chip, cutting the new one off at the left edge: every change starts the row over.
    val rowState = rememberLazyListState()
    LaunchedEffect(chips.map { it.first }) { rowState.scrollToItem(0) }
    LazyRow(
        state                 = rowState,
        contentPadding        = PaddingValues(horizontal = DsSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        modifier              = Modifier.padding(top = DsSpacing.sm)
    ) {
        items(chips, key = { it.first }) { (label, remove) ->
            Row(
                modifier = Modifier
                    .height(Step2ChipHeight)
                    .clip(DsShapes.pill)
                    .background(DsColors.PrimaryLight)
                    .clickable { remove() }
                    .padding(horizontal = DsSpacing.md),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
            ) {
                Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary, maxLines = 1)
                Icon(Icons.Default.Close, contentDescription = "Retirer", tint = DsColors.Primary, modifier = Modifier.size(14.dp))
            }
        }
        item(key = "clear-all") {
            Box(
                modifier = Modifier
                    .height(Step2ChipHeight)
                    .clip(DsShapes.pill)
                    .clickable { onClearAll() }
                    .padding(horizontal = DsSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("Tout effacer", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
            }
        }
    }
}

/**
 * The sheet. Changes apply as they are made, as in Produits: "Appliquer" only closes it, and
 * "Réinitialiser" clears the criteria but not the search. A commune belongs to a wilaya, so it is
 * offered once one is chosen. [types] and [secteurs] are the Clients-only sections; null hides them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartyFilterSheet(
    resultCount : Int,
    wilayas     : List<String>,
    wilaya      : String?,
    onWilaya    : (String?) -> Unit,
    communes    : List<String>,
    commune     : String?,
    onCommune   : (String?) -> Unit,
    balance     : BalanceFilter?,
    onBalance   : (BalanceFilter?) -> Unit,
    onReset     : () -> Unit,
    onDismiss   : () -> Unit,
    types       : List<Pair<String, String>>? = null,
    type        : String? = null,
    onType      : (String?) -> Unit = {},
    secteurs    : List<String>? = null,
    secteur     : String? = null,
    onSecteur   : (String?) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface
    ) {
        Column(
            modifier = Modifier
                .padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.xxl)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Filtres", fontWeight = FontWeight.Bold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DsColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(DsSpacing.sm))

            if (types != null) {
                Segments("Type de client", listOf<Pair<String?, String>>(null to "Tous") + types, type, onType)
            }

            Segments("Solde", listOf<Pair<BalanceFilter?, String>>(null to "Tous") + BalanceFilter.entries.map { it to it.label }, balance, onBalance)

            FilterDropdown(
                label    = "Wilaya",
                allLabel = "Toutes les wilayas",
                options  = wilayas,
                selected = wilaya,
                keyOf    = { it },
                nameOf   = { it },
                onSelect = { onWilaya(it) }
            )
            FilterDropdown(
                label    = "Commune",
                allLabel = "Toutes les communes",
                options  = communes,
                selected = commune,
                keyOf    = { it },
                nameOf   = { it },
                enabled  = wilaya != null,
                onSelect = { onCommune(it) }
            )
            if (secteurs != null) {
                FilterDropdown(
                    label    = "Secteur",
                    allLabel = "Tous les secteurs",
                    options  = secteurs,
                    selected = secteur,
                    keyOf    = { it },
                    nameOf   = { it },
                    onSelect = { onSecteur(it) }
                )
            }
            Spacer(Modifier.height(DsSpacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                OutlinedButton(
                    onClick  = { onReset(); onDismiss() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.TextPrimary),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Text("Réinitialiser", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Text("Appliquer ($resultCount)", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

/** One choice among a few, side by side — the unit selector of the product sheet. */
@Composable
private fun <K> Segments(label: String, options: List<Pair<K?, String>>, selected: K?, onSelect: (K?) -> Unit) {
    Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        options.forEach { (value, name) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(DsShapes.medium)
                    .background(if (active) DsColors.Primary else DsColors.Surface)
                    .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    fontSize   = DsTextSize.caption,
                    fontWeight = FontWeight.Medium,
                    color      = if (active) Color.White else DsColors.TextPrimary,
                    maxLines   = 1
                )
            }
        }
    }
    Spacer(Modifier.height(DsSpacing.md))
}
